from sklearn.metrics import cohen_kappa_score
from idx_xml_utils import load_idx_xml_file


def find_slot(ref_slot, ie_frame):
    for s in ie_frame.slots:
        if s.start == ref_slot.start and s.end == ref_slot.end:
            return s
    return None


def find_rel(ref_rel, rels):
    slot1 = ref_rel.slots[0]
    slot2 = ref_rel.slots[1]
    for r in rels:
        s1 = find_slot(slot1, r)
        s2 = find_slot(slot2, r)
        if s1 and s2:
            return r
    return None


def prep_annotator_labels(si_list1, si_list2, only_ac_fc=False):
    si_map = {}
    for si in si_list2:
        key = '{}:{}'.format(si.doc_idx, si.sent_idx)
        si_map[key] = si
        y1, y2 = [], []
        for si in si_list1:
            key = '{}:{}'.format(si.doc_idx, si.sent_idx)
            if key not in si_map:
                continue
            si2 = si_map[key]
            if len(si.ie_frame_list) != len(si2.ie_frame_list):
                continue
            for rel in si.ie_frame_list:
                rel2 = find_rel(rel, si2.ie_frame_list)
                if rel2:
                    type1 = rel.type
                    type2 = rel2.type
                    if only_ac_fc:
                        if type1.startswith('anatomical') or type1.startswith('functional') or type2.startswith('anatomical') or type2.startswith('functional'):
                            pass
                        else:
                            continue

                    #if type1.endswith('connectivity'):
                    #    type1 = 'connectivity'
                    #if type2.endswith('connectivity'):
                    #    type2 = 'connectivity'
                    if type1.startswith('general'):
                        type1 = 'no-relation'
                    if type2.startswith('general'):
                        type2 = 'no-relation'
                    y1.append(type1)
                    y2.append(type2)
    return y1, y2


def load_si_list(idx_xml_file, max_sent=200):
    fi = load_idx_xml_file(idx_xml_file)
    si_list = []
    count = 0
    for di in fi.di_list:
        for si in di.si_list:
            count += 1
            if count > max_sent:
                return si_list
            if len(si.ie_frame_list) > 0:
                si_list.append(si)
    return si_list


if __name__ == '__main__':
    root_dir = '/home/bozyurt/dev/java/bnlp-re/data/sparc/iaa_sets'
    idx_file2 = '/tmp/sparc_connectivity_nerve_ganglia_sampled_idx_maryann2.xml'
    idx_file1 = '/tmp/sparc_connectivity_nerve_ganglia_sampled_idx_joe.xml'

    idx_file1 = root_dir + '/sparc_connectivity_nerve_ganglia_sampled_test_idx_joe.xml'
    idx_file2 = root_dir + '/sparc_connectivity_nerve_ganglia_sampled_test_idx_maryann.xml'
    si_list1 = load_si_list(idx_file1)
    si_list2 = load_si_list(idx_file2)
    y1, y2 = prep_annotator_labels(si_list1, si_list2, only_ac_fc=True)
    print('y1:', len(y1))
    print('y2:', len(y2))
    count = 0
    for a, b in zip(y1, y2):
        print(f'{a} -> {b}')
        if a == b:
            count += 1
    print("% agreement: {}".format(count/len(y1) * 100.0))
    kappa = cohen_kappa_score(y1, y2)
    print('kappa:', kappa)








