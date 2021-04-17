import pickle
import numpy as np


def load_results(pickle_file, task, task_name):
    with open(pickle_file, "rb") as f:
        data = pickle.load(f)
    p_ac_list = []
    r_ac_list = []
    f1_ac_list = []
    p_fc_list, r_fc_list, f1_fc_list = [], [], []
    for run in data:
        p_ac_list.append(float(run[task]['precision_AC']))
        r_ac_list.append(float(run[task]['recall_AC']))
        f1_ac_list.append(float(run[task]['f1_AC']))
        p_fc_list.append(float(run[task]['precision_FC']))
        r_fc_list.append(float(run[task]['recall_FC']))
        f1_fc_list.append(float(run[task]['f1_FC']))
    tmpl = "{}  - P: {:.2f} ({:.2f}) R: {:.2f} ({:.2f}) F1: {:.2f} ({:.2f})"
    pam = np.mean(p_ac_list)
    ram = np.mean(r_ac_list)
    f1am = np.mean(f1_ac_list)
    pa_std = np.std(p_ac_list)
    ra_std = np.std(r_ac_list)
    f1a_std = np.std(f1_ac_list)
    pfm = np.mean(p_fc_list)
    rfm = np.mean(r_fc_list)
    f1fm = np.mean(f1_fc_list)
    pf_std = np.std(p_fc_list)
    rf_std = np.std(r_fc_list)
    f1f_std = np.std(f1_fc_list)
    print(tmpl.format(task_name + ' AC', pam, pa_std, ram, ra_std, f1am, f1a_std))
    print(tmpl.format(task_name + ' FC', pfm, pf_std, rfm, rf_std, f1fm, f1f_std))


def main():
    root_dir = '/home/bozyurt/dev/python/bioasq_electra/data/models'
    pickle_file = root_dir + '/bio_electra_base_1_2M/results/sparc-multi_results.pkl'
    load_results(pickle_file, 'sparc-multi', 'SPARC')

if __name__ == '__main__':
    main()

