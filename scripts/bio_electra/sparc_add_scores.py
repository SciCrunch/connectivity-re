import pickle
import csv
import argparse
import numpy as np

def add_scores(test_pickle_file, test_tsv_file, pred_tsv_file):
    with open(test_pickle_file, "rb") as f:
        data = pickle.load(f)

    # import pdb; pdb.set_trace()
    lines = []
    with open(test_tsv_file, "r") as f:
        reader = csv.reader(f, delimiter='\t')
        for i, row in enumerate(reader):
            if i == 0:
                continue
            probs = np.exp(data[i-1])/ np.sum(np.exp(data[i-1]))
            line = [row[0], probs[0], probs[1]]
            lines.append(line)

    with open(pred_tsv_file, 'w') as f:
        writer = csv.writer(f, delimiter='\t')
        writer.writerow(['sentence','no_rel_prob', 'rel_prob'])
        for line in lines:
            writer.writerow(line)
    print('wrote ', pred_tsv_file)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-i', action='store', help="in-tsv-file", required=True)
    parser.add_argument('-o', action='store', help="out-predictions-file", required=True)
    args = parser.parse_args()
   
    root_dir = '/home/bozyurt/dev/python/bioasq_electra/data/'
    in_tsv_file = root_dir + 'finetuning_data/sparc/test.tsv'
    predictions_file = '/tmp/sparc_predictions.txt'
    in_tsv_file = args.i
    predictions_file = args.o
    add_scores(root_dir + 'models/bio_electra_base_1_2M/test_predictions/sparc_test_1_predictions.pkl',
           in_tsv_file, predictions_file)
