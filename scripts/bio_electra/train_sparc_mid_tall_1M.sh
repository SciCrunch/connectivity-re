#!/bin/bash
  
DATA_DIR=/home/bozyurt/dev/python/bioasq_electra/data
# for Python 3 virtual environment with TensorFlow 2 
source $HOME/tf1_15_env/bin/activate

python run_finetuning.py --data-dir $DATA_DIR --model-name bio_electra_mid_tall_1M --hparams '{"model_size": "mid-tall", "task_names": ["sparc"], "max_seq_length":128, "train_batch_size":16, "do_train":true, "use_tfrecords_if_existing": false, "vocab_file": "/home/bozyurt/dev/python/electra/data/pmc_2017_abstracts_wp_vocab_sorted.txt", "vocab_size": 31620, "num_trials": 10, "write_test_outputs": true, "n_writes_test": 10}'

