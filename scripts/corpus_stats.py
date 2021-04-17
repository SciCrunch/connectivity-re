from idx_xml_utils import load_idx_xml_file
import matplotlib.pyplot as plt
from scipy import stats


def prep_stats(idx_xml_file):
    fi = load_idx_xml_file(idx_xml_file)
    count = 0
    rel_count = 0
    rel_lengths = []
    for di in fi.di_list:
        for si in di.si_list:
            if len(si.ie_frame_list) > 0:
                sentence = si.text.text
                for rel in si.ie_frame_list:
                    count += 1
                    if rel.type.startswith('anatomical') or rel.type.startswith('functional'):
                        rel_count += 1
                        rel_lengths.append(len(sentence))
    print(f"rel_count:{rel_count} count:{count}")
    print(stats.describe(rel_lengths))
    n, bins, patches = plt.hist(rel_lengths, 10)
    plt.show()


if __name__ == '__main__':
    root_dir = '/home/bozyurt/dev/java/bnlp-re/data/sparc/base'
    idx_xml_file = root_dir + '/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml'
    # idx_xml_file =  root_dir + '/active_learning/iterations/full_train_idx.xml'
    prep_stats(idx_xml_file)


