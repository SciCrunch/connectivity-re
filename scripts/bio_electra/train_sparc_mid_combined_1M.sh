#!/bin/bash
  
DATA_DIR=$CONNECTIVITY_RE_HOME/scripts/bio_electra/data
source $CONNECTIVITY_RE_HOME/venv/bin/activate

python run_finetuning.py --data-dir $DATA_DIR --model-name bio_electra_mid_combined --hparams '{"model_size": "mid", "task_names": ["sparc"], "max_seq_length":128, "train_batch_size":24, "do_train":true, "use_tfrecords_if_existing": false, "vocab_file": "'"$CONNECTIVITY_RE_HOME"'/scripts/bio_electra/data/pmc_2017_abstracts_wp_vocab_sorted.txt", "vocab_size": 31620, "num_trials": 10, "write_test_outputs": true, "n_writes_test": 10}'

