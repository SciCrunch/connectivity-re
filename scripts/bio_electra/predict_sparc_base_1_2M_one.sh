#!/bin/bash
  
while getopts 'i:o:' OPTION; do
    case "$OPTION" in
        i)
            PRED_IN_TSV_FILE="$OPTARG"
            ;;
        o)
            PRED_OUT_FILE="$OPTARG"
            ;;
        ?)
            echo "Usage: $(basename $0) -i <pred-in-tsv--file> -o <pred-out-file>" >&2
            exit 1
            ;;
    esac
done
shift "$(($OPTIND -1))"

if ((OPTIND != 5))
then
    echo "Usage: $(basename $0) -i <pred-in-tsv--file> -o <pred-out-file>" >&2
    exit 1
fi


DATA_DIR=$CONNECTIVITY_RE_HOME/scripts/bio_electra/data
source $CONNECTIVITY_RE_HOME/venv/bin/activate

cp $PRED_IN_TSV_FILE $DATA_DIR/finetuning_data/sparc/test.tsv

python run_finetuning.py --predict --data-dir $DATA_DIR --model-name bio_electra_base --hparams '{"model_size": "base", "task_names": ["sparc"], "max_seq_length":128, "train_batch_size":16, "use_tfrecords_if_existing": false, "vocab_file": "'"$CONNECTIVITY_RE_HOME"'/scripts/bio_electra/data/pmc_2017_abstracts_wp_vocab_sorted.txt", "vocab_size": 31620, "num_trials": 1, "write_test_outputs": true, "n_writes_test": 1}'

python sparc_add_scores.py -i $PRED_IN_TSV_FILE -o $PRED_OUT_FILE
