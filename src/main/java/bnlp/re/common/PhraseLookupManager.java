package bnlp.re.common;

import bnlp.common.ITokenizer;
import bnlp.common.types.Node;
import bnlp.nlp.sentence.TokenInfo;
import bnlp.re.util.Assertion;

import java.io.IOException;
import java.util.*;

public class PhraseLookupManager {
    Set<String> firstWordLT = new HashSet<String>();
    Set<String> ltSet = new HashSet<String>();
    protected boolean useDownCasing;
    protected Map<String, Node> trieMap = new HashMap<>();
    protected boolean useTrie = true;
    protected Tokenizer tokenizer;

    public PhraseLookupManager(List<String> lookupTable, boolean useDownCasing,
                               Tokenizer tokenizer) throws IOException {
        this.useDownCasing = useDownCasing;
        this.tokenizer = tokenizer;
        initialize(lookupTable, useDownCasing, tokenizer);
    }

    public PhraseLookupManager(List<String> lookupTable, boolean useDownCasing,
                               Tokenizer tokenizer, boolean useTrie) throws IOException {
        this.useDownCasing = useDownCasing;
        this.useTrie = useTrie;
        this.tokenizer = tokenizer;
        initialize(lookupTable, useDownCasing, tokenizer);
    }

    private void initialize(List<String> lookupTable, boolean useDownCasing, Tokenizer tokenizer) throws IOException {
        for (String item : lookupTable) {
            item = item.trim();
            if (item.length() == 1) {
                continue;
            }
            if (useDownCasing) {
                item = item.toLowerCase();
            }

            List<TokenInfo> tiList = tokenizer.tokenize(item);
            firstWordLT.add(tiList.get(0).getTokValue());
            StringBuilder sb = new StringBuilder();
            for (TokenInfo ti : tiList) {
                sb.append(ti.getTokValue()).append(' ');
            }
            ltSet.add(sb.toString().trim());
            if (useTrie) {
                String[] tokens = new String[tiList.size()];
                for (int i = 0; i < tiList.size(); i++) {
                    tokens[i] = tiList.get(i).getTokValue();
                }
                add2Trie(tokens);
            }
        }

    }

    void add2Trie(String[] tokens) {
        String firstToken = tokens[0];
        Node rootNode = trieMap.get(firstToken);
        if (rootNode == null) {
            rootNode = new Node(firstToken, null);
            trieMap.put(firstToken, rootNode);
        }
        Node p = rootNode;
        Node child = null;
        for (int i = 1; i < tokens.length; i++) {
            String tok = tokens[i];
            if (p.hasChildren()) {
                child = p.findByUserObject(tok);
                if (child == null) {
                    child = new Node(tok);
                    p.addChild(child);
                }
            } else {
                child = new Node(tok);
                p.addChild(child);
            }
            p = child;
        }
        // add sentinel
        p.addChild(new Node("<EOP>"));
    }

    public Span findLongestMatching(String content) throws IOException {
        if (useDownCasing) {
            content = content.toLowerCase();
        }
        List<TokenInfo> tiList = tokenizer.tokenize(content);
        int numToks = tiList.size();
        int offset = 0;
        for (int i = 0; i < numToks; i++) {
            TokenInfo ti = tiList.get(i);
            String token = ti.getTokValue();
            if (hasPrefix(token)) {
                StringBuilder sb = new StringBuilder();
                sb.append(token);
                int startIdx = ti.getStart();
                int endIdx = ti.getEnd();
                Node p = trieMap.get(token);
                Assertion.assertNotNull(p);
                boolean found = false;
                while (i < numToks - 1) {
                    i++;
                    ti = tiList.get(i);
                    Node child = p.findByUserObject(ti.getTokValue());
                    if (child == null) {
                        if (p.findByUserObject("<EOP>") != null) {
                            found = true;
                        }
                        break;
                    } else {
                        sb.append(' ').append(ti.getTokValue());
                        p = child;
                        endIdx = ti.getEnd();
                    }
                }
                if (found) {
                    return new Span(startIdx, endIdx);
                }
            }
        }
        return null;
    }

    public boolean isPrefixInTrie(String prefix) {
        if (useDownCasing) {
            prefix = prefix.toLowerCase();
        }
        String[] toks = prefix.split("\\s+");
        Node p = trieMap.get(toks[0]);
        if (p == null)
            return false;
        for (int i = 1; i < toks.length; i++) {
            Node child = p.findByUserObject(toks[i]);
            if (child == null) {
                return false;
            }
            p = child;
        }
        return true;
    }

    public boolean isInTrieFully(String phrase) {
        if (useDownCasing) {
            phrase = phrase.toLowerCase();
        }
        String[] toks = phrase.split("\\s+");
        Node p = trieMap.get(toks[0]);
        if (p == null)
            return false;
        if (toks.length == 1) {
            return p.findByUserObject("<EOP>") != null;
        }
        for (int i = 1; i < toks.length; i++) {
            Node child = p.findByUserObject(toks[i]);
            if (child == null) {
                return false;
            }
            p = child;
        }
        return p.findByUserObject("<EOP>") != null;
    }

    public PhraseLookupManager(List<String> lookupTable, boolean useDownCasing) {
        this.useDownCasing = useDownCasing;
        initialize(lookupTable);
    }

    public PhraseLookupManager(boolean useDownCasing) {
        this.useDownCasing = useDownCasing;
    }

    public void initialize(List<String> lookupTable) {
        for (String item : lookupTable) {
            item = item.trim();
            if (item.length() == 1) {
                continue;
            }
            if (useDownCasing) {
                item = item.toLowerCase();
            }
            int idx = item.indexOf(' ');
            if (idx == -1) {
                firstWordLT.add(item);
                ltSet.add(item);
            } else {
                String first = item.substring(0, idx);
                firstWordLT.add(first);
                ltSet.add(item);
                if (useTrie) {
                    String[] tokens = item.split("\\s+");
                    add2Trie(tokens);
                }
            }
        }
    }

    public boolean isInLexicon(String phrase) {
        if (useDownCasing) {
            phrase = phrase.toLowerCase();
        }
        return ltSet.contains(phrase);
    }

    public boolean hasPrefix(String prefix) {
        if (useDownCasing) {
            prefix = prefix.toLowerCase();
        }
        return firstWordLT.contains(prefix);
    }


    public Set<String> getFirstWordLT() {
        return firstWordLT;
    }
}
