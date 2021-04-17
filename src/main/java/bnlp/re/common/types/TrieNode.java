package bnlp.re.common.types;

import java.io.Serializable;
import java.util.*;

/**
 * Created by bozyurt on 10/22/20.
 */
public class TrieNode implements Serializable {
    protected Object userObject;
    protected TrieNode parent;
    protected List<TrieNode> children;
    protected Map<Object, List<TrieNode>> childMap;
    private static final long serialVersionUID = 1L;

    public TrieNode(Object userObject, TrieNode parent) {
        this.userObject = userObject;
        this.parent = parent;
    }

    public TrieNode(Object userObject) {
        this(userObject, null);
    }

    public void addChild(TrieNode child) {
        if (this.children == null) {
            this.children = new LinkedList();
        }

        child.parent = this;
        this.children.add(child);
    }

    public boolean hasChildren() {
        return this.children != null && !this.children.isEmpty();
    }

    public List<TrieNode> findByUserObject(Object prototype) {
        if (!this.hasChildren()) {
            return null;
        } else {
            if (this.children.size() <= 20) {
                List<TrieNode> list = new ArrayList<>(1);
                for (TrieNode child : children) {
                    if (child.userObject.equals(prototype)) {
                        list.add(child);
                    }
                }
                return list;
            } else {
                if (this.childMap == null) {
                    System.out.println(this.userObject + " - children size:" + this.children.size());
                    this.childMap = new HashMap();
                    for (TrieNode child : children) {
                        List<TrieNode> list = this.childMap.get(child.userObject);
                        if (list == null) {
                            list = new ArrayList<>(1);
                            this.childMap.put(child.userObject, list);
                        }
                        list.add(child);
                    }
                }

                return this.childMap.get(prototype);
            }
        }
    }
}
