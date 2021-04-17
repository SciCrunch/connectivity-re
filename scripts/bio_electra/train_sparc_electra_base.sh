#!/bin/bash
  
DATA_DIR=$CONNECTIVITY_RE_HOME/scripts/bio_electra/data
source $CONNECTIVITY_RE_HOME/venv/bin/activate

python run_finetuning.py --data-dir $DATA_DIR --model-name electra_base --hparams '{"model_size": "base", "task_names": ["sparc"], "max_seq_length":128, "train_batch_size":16, "do_train":true, "use_tfrecords_if_existing": false, "num_trials": 10, "write_test_outputs": true, "n_writes_test": 10}'

