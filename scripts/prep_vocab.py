import csv
import re
import json


def load_terms(csv_file):
    term_synonyms_map = {}
    with open(csv_file) as f:
        reader = csv.reader(f, delimiter=',')
        first = True
        for row in reader:
            if first:
                first = False
                continue
            if row[2] != 'Keep':
                continue
            term = row[0]
            term_synonyms_map[term] = []
            synonyms_str = row[4]
            if synonyms_str:
                synonyms = re.split('\\s*,\\s*', synonyms_str)
                term_synonyms_map[term].extend(synonyms)
    return term_synonyms_map


def prep_vocab2(out_vocab_json_file):
    t1_map = load_terms('nerves.csv')
    t2_map = load_terms('ganglia.csv')
    t_map = dict(t1_map)
    t_map.update(t2_map)
    with open(out_vocab_json_file, 'w') as f:
        json.dump(t_map, f, indent=2)
    print('wrote ', out_vocab_json_file)


def prep_vocab(out_vocab_file):
    t1_map = load_terms('nerves.csv')
    t2_map = load_terms('ganglia.csv')
    # import pdb; pdb.set_trace()
    t_map = dict(t1_map)
    t_map.update(t2_map)
    with open(out_vocab_file, 'w') as f:
        for term, synonyms in t_map.items():
            f.write(term+'\n')
            if len(synonyms) > 0:
                for synonym in synonyms:
                    f.write(synonym + '\n')
    print('wrote ', out_vocab_file)


if __name__ == '__main__':
    # prep_vocab('nerve_ganglia_vocab.txt')
    prep_vocab2('nerve_ganglia_vocab_syn.json')

