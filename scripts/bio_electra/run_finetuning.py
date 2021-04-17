# coding=utf-8
# Copyright 2020 The Google Research Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Fine-tunes an ELECTRA model on a downstream task."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import argparse
import collections
import json

import tensorflow.compat.v1 as tf

import configure_finetuning
from finetune import preprocessing
from finetune import task_builder
from model import modeling
from model import optimization
from util import training_utils
from util import utils

# IBO
from finetune import  feature_spec


EXPORT_MODE = False


def get_task_id_feature(features):
    if 'task_id' in features:
        return features['task_id']
    else:
        return feature_spec.FeatureSpec("task_id", [])



class FinetuningModel(object):
  """Finetuning model with support for multi-task training."""

  def __init__(self, config: configure_finetuning.FinetuningConfig, tasks,
               is_training, features, num_train_steps):
    # Create a shared transformer encoder
    bert_config = training_utils.get_bert_config(config)
    self.bert_config = bert_config
    if config.debug:
      bert_config.num_hidden_layers = 3
      bert_config.hidden_size = 144
      bert_config.intermediate_size = 144 * 4
      bert_config.num_attention_heads = 4
    assert config.max_seq_length <= bert_config.max_position_embeddings
    bert_model = modeling.BertModel(
        bert_config=bert_config,
        is_training=is_training,
        input_ids=features["input_ids"],
        input_mask=features["input_mask"],
        token_type_ids=features["segment_ids"],
        use_one_hot_embeddings=config.use_tpu,
        embedding_size=config.embedding_size)
    if is_training:
        percent_done = (tf.cast(tf.train.get_or_create_global_step(), tf.float32) /
                     tf.cast(num_train_steps, tf.float32))
    else:
        percent_done = None

    # Add specific tasks
    self.outputs = {"task_id": features["task_id"]}
    losses = []
    for task in tasks:
      with tf.variable_scope("task_specific/" + task.name):
        task_losses, task_outputs = task.get_prediction_module(
            bert_model, features, is_training, percent_done)
        losses.append(task_losses)
        self.outputs[task.name] = task_outputs
    self.loss = tf.reduce_sum(tf.stack(losses, -1) *
                    tf.one_hot(features["task_id"], len(config.task_names)))


#IBO
def show_num_model_params(tvars):
    total = 0
    for tvar in tvars:
        shape = tvar.get_shape()
        var_params = 1
        for dim in shape:
            var_params *= dim.value
        total += var_params
    print("# of trainable parameters", total)


def model_fn_builder(config: configure_finetuning.FinetuningConfig, tasks,
                     num_train_steps, pretraining_config=None):
  """Returns `model_fn` closure for TPUEstimator."""

  def model_fn(features, labels, mode, params):
    """The `model_fn` for TPUEstimator."""
    utils.log("Building model...")
    # import pdb; pdb.set_trace()
    is_training = (mode == tf.estimator.ModeKeys.TRAIN)
    model = FinetuningModel(
        config, tasks, is_training, features, num_train_steps)

    # Load pre-trained weights from checkpoint
    init_checkpoint = config.init_checkpoint
    if pretraining_config is not None:
      init_checkpoint = tf.train.latest_checkpoint(pretraining_config.model_dir)
      utils.log("Using checkpoint", init_checkpoint)
    tvars = tf.trainable_variables()
    show_num_model_params(tvars)
    scaffold_fn = None
    if init_checkpoint:
      assignment_map, _ = modeling.get_assignment_map_from_checkpoint(
          tvars, init_checkpoint)
      if config.use_tpu:
        def tpu_scaffold():
          tf.train.init_from_checkpoint(init_checkpoint, assignment_map)
          return tf.train.Scaffold()
        scaffold_fn = tpu_scaffold
      else:
        print('initializing from checkppoint:', init_checkpoint)
        tf.train.init_from_checkpoint(init_checkpoint, assignment_map)

    # Build model for training or prediction
    if mode == tf.estimator.ModeKeys.TRAIN:
      train_op = optimization.create_optimizer(
          model.loss, config.learning_rate, num_train_steps,
          weight_decay_rate=config.weight_decay_rate,
          use_tpu=config.use_tpu,
          warmup_proportion=config.warmup_proportion,
          layerwise_lr_decay_power=config.layerwise_lr_decay,
          n_transformer_layers=model.bert_config.num_hidden_layers
      )
      output_spec = tf.estimator.tpu.TPUEstimatorSpec(
          mode=mode,
          loss=model.loss,
          train_op=train_op,
          scaffold_fn=scaffold_fn,
          training_hooks=[training_utils.ETAHook(
              {} if config.use_tpu else dict(loss=model.loss),
              num_train_steps, config.iterations_per_loop, config.use_tpu, 10)])
    else:
      assert mode == tf.estimator.ModeKeys.PREDICT
      utils.log("prediction mode")
      # import pdb; pdb.set_trace()
      output_spec = tf.estimator.tpu.TPUEstimatorSpec(
          mode=mode,
          predictions=utils.flatten_dict(model.outputs),
          scaffold_fn=scaffold_fn)

    utils.log("Building complete")
    return output_spec

  return model_fn

# IBO
def serving_input_fn_builder(feature_specs):
    # import pdb; pdb.set_trace()

    def serving_input_fn():
        features = collections.OrderedDict()
        in_set = {'attrition_label_ids', 'input_ids', 'input_mask', 'segment_ids',
                  'task_id', 'attrition_eid'}

        for spec in feature_specs:
            if spec.name not in in_set:
                continue
            shape = [None, spec.shape[0]] if spec.shape else [None]
            features[spec.name] = tf.placeholder(tf.int64 if spec.is_int_feature else tf.float32,
                    shape, name=spec.name)

        print("serving input.")
        input_fn = tf.estimator.export.build_raw_serving_input_receiver_fn(features)()
        return input_fn

    return serving_input_fn




class ModelRunner(object):
  """Fine-tunes a model on a supervised task."""

  def __init__(self, config: configure_finetuning.FinetuningConfig, tasks,
               pretraining_config=None):
    self._config = config
    self._tasks = tasks
    self._preprocessor = preprocessing.Preprocessor(config, self._tasks)

    is_per_host = tf.estimator.tpu.InputPipelineConfig.PER_HOST_V2
    tpu_cluster_resolver = None
    if config.use_tpu and config.tpu_name:
      tpu_cluster_resolver = tf.distribute.cluster_resolver.TPUClusterResolver(
          config.tpu_name, zone=config.tpu_zone, project=config.gcp_project)
    tpu_config = tf.estimator.tpu.TPUConfig(
        iterations_per_loop=config.iterations_per_loop,
        num_shards=config.num_tpu_cores,
        per_host_input_for_training=is_per_host,
        tpu_job_name=config.tpu_job_name)
    run_config = tf.estimator.tpu.RunConfig(
        cluster=tpu_cluster_resolver,
        model_dir=config.model_dir,
        save_checkpoints_steps=config.save_checkpoints_steps,
        save_checkpoints_secs=None,
        tpu_config=tpu_config)

    if self._config.do_train:
      (self._train_input_fn,
       self.train_steps) = self._preprocessor.prepare_train()
    else:
      self._train_input_fn, self.train_steps = None, 0
    print("in ModelRunner")
    model_fn = model_fn_builder(
        config=config,
        tasks=self._tasks,
        num_train_steps=self.train_steps,
        pretraining_config=pretraining_config)
    self._estimator = tf.estimator.tpu.TPUEstimator(
        use_tpu=config.use_tpu,
        model_fn=model_fn,
        config=run_config,
        train_batch_size=config.train_batch_size,
        eval_batch_size=config.eval_batch_size,
        predict_batch_size=config.predict_batch_size)

  def train(self):
    utils.log("Training for {:} steps".format(self.train_steps))
    self._estimator.train(
        input_fn=self._train_input_fn, max_steps=self.train_steps)

  def evaluate(self, export_dir=None):
    # import pdb; pdb.set_trace()  
    return {task.name: self.evaluate_task(task, export_dir=export_dir) for task in self._tasks}

  # IBO
  def evaluate_on_test(self):
    return {task.name: self.evaluate_task(task, split="test") for task in self._tasks}

  def evaluate_task(self, task, split="dev", return_results=True,
                    export_dir=None):
    """Evaluate the current model."""
    utils.log("Evaluating", task.name)

    if export_dir:
        assert len(self._tasks) == 1
        print('Exporting model to ', export_dir)
        feature_specs = self._preprocessor._feature_specs
        self._estimator.export_to_tpu = False
        serving_input_fn = serving_input_fn_builder(feature_specs)
        self._estimator.export_savedmodel(export_dir, serving_input_fn)
        print("exported to ", export_dir)

    eval_input_fn, _ = self._preprocessor.prepare_predict([task], split)
    results = self._estimator.predict(input_fn=eval_input_fn,
                                      yield_single_examples=True)
    utils.log("done predictions")


    # import pdb; pdb.set_trace()  
    scorer = task.get_scorer()
    for r in results:
      if r["task_id"] != len(self._tasks):  # ignore padding examples
        r = utils.nest_dict(r, self._config.task_names)
        scorer.update(r[task.name])



    if return_results:
      utils.log(task.name + ": " + scorer.results_str())
      utils.log()
      return dict(scorer.get_results())
    else:
      return scorer

  def write_classification_outputs(self, tasks, trial, split):
    """Write classification predictions to disk."""
    utils.log("Writing out predictions for", tasks, split)
    predict_input_fn, _ = self._preprocessor.prepare_predict(tasks, split)
    results = self._estimator.predict(input_fn=predict_input_fn,
                                      yield_single_examples=True)
    # task name -> eid -> model-logits
    logits = collections.defaultdict(dict)
    for r in results:
      if r["task_id"] != len(self._tasks):
        r = utils.nest_dict(r, self._config.task_names)
        task_name = self._config.task_names[r["task_id"]]
        logits[task_name][r[task_name]["eid"]] = (
            r[task_name]["logits"] if "logits" in r[task_name]
            else r[task_name]["predictions"])
    for task_name in logits:
      utils.log("Pickling predictions for {:} {:} examples ({:})".format(
          len(logits[task_name]), task_name, split))
      if trial <= self._config.n_writes_test:
        utils.write_pickle(logits[task_name], self._config.test_predictions(
            task_name, split, trial))


def write_results(config: configure_finetuning.FinetuningConfig, results):
  """Write evaluation metrics to disk."""
  utils.log("Writing results to", config.results_txt)
  utils.mkdir(config.results_txt.rsplit("/", 1)[0])
  utils.write_pickle(results, config.results_pkl)
  with tf.io.gfile.GFile(config.results_txt, "w") as f:
    results_str = ""
    for trial_results in results:
      for task_name, task_results in trial_results.items():
        if task_name == "time" or task_name == "global_step":
          continue
        results_str += task_name + ": " + " - ".join(
            ["{:}: {:.2f}".format(k, v)
             for k, v in task_results.items()]) + "\n"
    f.write(results_str)
  utils.write_pickle(results, config.results_pkl)


def predict(config: configure_finetuning.FinetuningConfig):
  """using a trained model for task name, do predictions for test data set and save the results """
  no_trials = config.num_trials if config.num_trials > 0 else 1
  print(no_trials)
  generic_model_dir = config.model_dir
  for trial in range(1, no_trials+1):
      utils.log_config(config)
      tasks = task_builder.get_tasks(config)
      config.model_dir = generic_model_dir + "_" + str(trial)
      print("config.model_dir:{}".format(config.model_dir))
      model_runner = ModelRunner(config, tasks)
      utils.heading("Running on the test set and writing the predictions")
      for task in tasks:
          if task.name in ["cola", "mrpc", "mnli", "sst", "rte", "qnli", "qqp", "sts", "yesno", 
                           "reranker", "weighted-reranker","gad", "chemprot",
                           "ynn", "ynnss", "sparc", "sparc-multi",
                           "synergy-reranker", "yesno-snippet"]:
              for split in task.get_test_splits():
                  model_runner.write_classification_outputs([task], trial, split)



def run_finetuning(config: configure_finetuning.FinetuningConfig,
                   export_dir=None):
  """Run finetuning."""

  # Setup for training
  results = []
  trial = 1
  heading_info = "model={:}, trial {:}/{:}".format(
      config.model_name, trial, config.num_trials)
  heading = lambda msg: utils.heading(msg + ": " + heading_info)
  heading("Config")
  utils.log_config(config)
  generic_model_dir = config.model_dir
  tasks = task_builder.get_tasks(config)

  # Train and evaluate num_trials models with different random seeds
  while config.num_trials < 0 or trial <= config.num_trials:
    config.model_dir = generic_model_dir + "_" + str(trial)
    if config.do_train:
      utils.rmkdir(config.model_dir)

    # import pdb; pdb.set_trace()
    model_runner = ModelRunner(config, tasks)
    if config.do_train:
      heading("Start training")
      model_runner.train()
      utils.log()

    if config.do_eval:
      # IBO
      if config.do_eval_on_test:
        heading("Run test set evaluation")
        results.append(model_runner.evaluate_on_test())
      else:
        heading("Run dev set evaluation")
        results.append(model_runner.evaluate(export_dir=export_dir))
      write_results(config, results)
      if config.write_test_outputs and trial <= config.n_writes_test:
        heading("Running on the test set and writing the predictions")
        for task in tasks:
          # Currently only writing preds for GLUE and SQuAD 2.0 is supported
          if task.name in ["cola", "mrpc", "mnli", "sst", "rte", "qnli", "qqp",
                           "sts", "yesno", "gad", "chemprot", "sparc", "sparc-multi",
                           "yesno-snippet"]:
            for split in task.get_test_splits():
              model_runner.write_classification_outputs([task], trial, split)
          elif task.name == "squad":
            scorer = model_runner.evaluate_task(task, "test", False)
            scorer.write_predictions()
            preds = utils.load_json(config.qa_preds_file("squad"))
            null_odds = utils.load_json(config.qa_na_file("squad"))
            for q, _ in preds.items():
              if null_odds[q] > config.qa_na_threshold:
                preds[q] = ""
            utils.write_json(preds, config.test_predictions(
                task.name, "test", trial))
          else:
            utils.log("Skipping task", task.name,
                      "- writing predictions is not supported for this task")

    if trial != config.num_trials and (not config.keep_all_models):
      utils.rmrf(config.model_dir)
    trial += 1


def main():
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--data-dir", required=True,
                      help="Location of data files (model weights, etc).")
  parser.add_argument("--model-name", required=True,
                      help="The name of the model being fine-tuned.")
  parser.add_argument("--hparams", default="{}",
                      help="JSON dict of model hyperparameters.")
   # IBO
  parser.add_argument("--predict", action='store_true',
       help="prediction mode")
  parser.add_argument("--export-dir", help="export directory")

  args = parser.parse_args()
  export_dir = None
  if args.export_dir:
      export_dir = args.export_dir
  if args.hparams.endswith(".json"):
    hparams = utils.load_json(args.hparams)
  else:
    hparams = json.loads(args.hparams)
  tf.logging.set_verbosity(tf.logging.ERROR)
  if args.predict:
    predict(configure_finetuning.FinetuningConfig(
                args.model_name, args.data_dir, **hparams))
  else:
    run_finetuning(configure_finetuning.FinetuningConfig(
                args.model_name, args.data_dir, **hparams),
                export_dir=export_dir)


if __name__ == "__main__":
  main()
