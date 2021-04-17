from xml.etree.ElementTree import Element, SubElement
import xml.etree.ElementTree as ET


def get_child(parent, child_name):
    for child in parent.iter(child_name):
        return child
    return None


class BaseSlotInfo(object):
    def __init__(self, ne_type, start, end, status,
                 split_start=None, split_end=None):
        self.type = ne_type
        self.start = start
        self.end = end
        self.status = status
        self.split_start = split_start
        self.split_end = split_end

    @classmethod
    def from_xml(cls, node):
        ne_type = node.attrib['type']
        start = int(node.attrib['start'])
        end = int(node.attrib['end'])
        status = node.attrib['status']
        return cls(ne_type, start, end, status)


class BaseIEFrameInfo(object):
    def __init__(self, rel_type):
        self.type = rel_type
        self.slots = []

    @classmethod
    def from_xml(cls, node):
        rel_type = node.attrib['type']
        c = cls(rel_type)
        for child in node.iter('slot'):
            c.slots.append(BaseSlotInfo.from_xml(child))
        return c


class TextInfo(object):
    def __init__(self, idx, text, source=None):
        self.idx = idx
        self.text = text
        self.source = source

    @classmethod
    def from_xml(cls, node):
        idx = int(node.attrib['idx'])
        text = node.text
        source = node.attrib['src'] if 'src' in node.attrib else None
        return cls(idx, text, source)


class NEInfo(object):
    def __init__(self, ne_type, start, end, status):
        self.type = ne_type
        self.start = start
        self.end = end
        self.status = status

    def extract_ne(self, sentence):
        return sentence[self.start:self.end]

    @classmethod
    def from_xml(cls, node):
        ne_type = node.attrib['type']
        status = node.attrib['status']
        start = int(node.attrib['start'])
        end = int(node.attrib['end'])
        return cls(ne_type, start, end, status)


class SentenceInfo(object):
    def __init__(self, doc_idx, sent_idx, text: TextInfo,
                 pt: TextInfo):
        self.doc_idx = doc_idx
        self.sent_idx = sent_idx
        self.text = text
        self.pt = pt
        self.ne_list = []
        self.ie_frame_list = []

    def has_entities(self):
        return len(self.ne_list) > 0

    @classmethod
    def from_xml(cls, node, doc_idx):
        text_node = get_child(node, 'text')
        pt_node = get_child(node, 'pt')
        text = TextInfo.from_xml(text_node)
        pt = TextInfo.from_xml(pt_node)
        c = cls(doc_idx, text.idx, text, pt)
        for child in node.iter('ne'):
            c.ne_list.append(NEInfo.from_xml(child))
        for child in node.iter('base-frame'):
            c.ie_frame_list.append(BaseIEFrameInfo.from_xml(child))
        return c


class DocumentInfo(object):
    def __init__(self, doc_idx, pmid, description):
        self.doc_idx = doc_idx
        self.pmid = pmid
        self.description = description
        self.si_list = []

    @classmethod
    def from_xml(cls, node):
        idx = int(node.attrib['idx'])
        pmid = node.attrib['PMID'] if 'PMID' in node.attrib else None
        dn = get_child(node, 'description')
        desc = dn.text if dn else None
        c = cls(idx, pmid, desc)
        for child in node.iter('sentence'):
            c.si_list.append(SentenceInfo.from_xml(child, idx))
        return c


class FileInfo(object):
    def __init__(self):
        self.di_list = []

    @classmethod
    def from_xml(cls, node):
        c = cls()
        for child in node.iter('document'):
            c.di_list.append(DocumentInfo.from_xml(child))
        return c


def load_idx_xml_file(idx_xml_file):
    tree = ET.parse(idx_xml_file)
    fi = FileInfo.from_xml(tree)
    return fi


if __name__ == '__main__':
    idx_xml_file = '/tmp/sparc_connectivity_nerve_ganglia_sampled_idx.xml'
    fi = load_idx_xml_file(idx_xml_file)
    for di in fi.di_list:
        for si in di.si_list:
            sentence = si.text.text
            print(sentence)
            for ne in si.ne_list:
                print('\t', ne.extract_ne(sentence))
            for bf in si.ie_frame_list:
                print('\t', bf.type)
    print('# docs:', len(fi.di_list))
