#!/bin/bash
  
DATA_DIR=$CONNECTIVITY_RE_HOME/scripts/bio_electra/data
source $CONNECTIVITY_RE_HOME/venv/bin/activate

python run_finetuning.py --data-dir $DATA_DIR --model-name bio_electra_base_1_2M --hparams '{"model_size": "base", "task_names": ["sparc-multi"], "max_seq_length":128, "train_batch_size":16, "do_train":true, "use_tfrecords_if_existing": false, "vocab_file": "'"$CONNECTIVITY_RE_HOME"'/scripts/bio_electra/data/pmc_2017_abstracts_wp_vocab_sorted.txt", "vocab_size": 31620, "num_trials": 10, "write_test_outputs": true, "n_writes_test": 10, "num_train_epochs": 15, "learning_rate":5e-5}'
