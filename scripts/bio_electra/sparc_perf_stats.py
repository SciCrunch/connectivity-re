import pickle
import numpy as np


def load_results(pickle_file, task, task_name):
    with open(pickle_file, "rb") as f:
        data = pickle.load(f)
    p_list = []
    r_list = []
    f1_list = []
    for run in data:
        p_list.append(float(run[task]['precision']))
        r_list.append(float(run[task]['recall']))
        f1_list.append(float(run[task]['f1']))
    tmpl = "{}  - P: {:.2f} ({:.2f}) R: {:.2f} ({:.2f}) F1: {:.2f} ({:.2f})"
    pm = np.mean(p_list)
    rm = np.mean(r_list)
    f1m = np.mean(f1_list)
    p_std = np.std(p_list)
    r_std = np.std(r_list)
    f1_std = np.std(f1_list)
    print(tmpl.format(task_name, pm, p_std, rm, r_std, f1m, f1_std))


def main():
    root_dir = '/home/bozyurt/dev/python/bioasq_electra/data/models'
    pickle_file = root_dir + '/bio_electra_base_1_2M/results/sparc_results.pkl'
    load_results(pickle_file, 'sparc', 'sparc [base 1.2M]')
    pickle_file = root_dir + '/bio_electra_mid_tall_500k/results/sparc_results.pkl'
    load_results(pickle_file, 'sparc', 'sparc [mid-tall 500k]')
    pickle_file = root_dir + '/electra_base/results/sparc_results.pkl'
    load_results(pickle_file, 'sparc', 'sparc [electra base]')
    pickle_file = root_dir + '/bio_electra_mid_tall_1M/results/sparc_results.pkl'
    load_results(pickle_file, 'sparc', 'sparc [mid-tall 1M]')
    pickle_file = root_dir + '/bio_electra_mid_combined_1M/results/sparc_results.pkl'
    load_results(pickle_file, 'sparc', 'sparc [mid combined 1M]')

main()

