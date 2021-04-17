package bnlp.re.common;


import bnlp.re.util.FileUtils;
import bnlp.re.util.NumberUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author I. Burak Ozyurt
 * @version $Id$
 */
public class FrequencyTable<T> {
    protected Map<T, Integer> freqMap = new HashMap<>();
    protected List<Comparable<T>> sortedKeys;

    public void addValue(T value) {
        if (freqMap.containsKey(value)) {
            int sum = freqMap.get(value);
            sum++;
            freqMap.put(value, sum);
        } else {
            freqMap.put(value, 1);
        }
    }

    public Map<T, Integer> getFreqMap() {
        return freqMap;
    }

    public int getFrequency(T key) {
        return freqMap.get(key);
    }

    public void load(String tsvFile) throws IOException {
        BufferedReader in = null;
        try {
            in = FileUtils.newUTF8CharSetReader(tsvFile);
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                String[] tokens = line.split("\\t");
                if (tokens.length != 2) {
                    System.out.println(">> " + line);
                    System.out.println("Skipping...");
                    continue;
                }
                int freq = NumberUtils.toInt(tokens[1], -1);
                if (freq > 0) {
                    freqMap.put((T) tokens[0], freq);
                }
            }
        } finally {
            FileUtils.close(in);
        }
    }

    public void save(String tsvFile) throws IOException {
        BufferedWriter out = null;
        try {
            out = FileUtils.newUTF8CharSetWriter(tsvFile);
            for (Comparable<T> key : getSortedKeys()) {
                String term = key.toString();
                String normalizedTerm = term.replaceAll("[\\n\\r]+", " ");
                out.write(normalizedTerm + "\t" + freqMap.get(term));
                out.newLine();
            }
            System.out.println("saved " + tsvFile);
        } finally {
            FileUtils.close(out);
        }
    }

    @SuppressWarnings("unchecked")
    public void orderKeys() {
        sortedKeys = new ArrayList<>(freqMap.size());
        for (T key : freqMap.keySet()) {
            sortedKeys.add((Comparable<T>) key);
        }
        Collections.sort(sortedKeys, (o1, o2) -> o1.compareTo((T) o2));
    }

    public void dumpSortedByFreq() {
        List<Freq<T>> list = new ArrayList<Freq<T>>(freqMap.size());
        int total = 0;
        for (T key : freqMap.keySet()) {
            Freq<T> freq = new Freq<>(key, freqMap.get(key));
            list.add(freq);
            total += freq.count;
        }
        Collections.sort(list, (o1, o2) -> o1.count - o2.count);
        System.out.println("-----------------------------");
        for (Freq<T> freq : list) {
            System.out.println(freq);
        }
        System.out.println("Total:" + total);
    }

    public List<Freq<T>> getSortedByFreq(boolean descending) {
        List<Freq<T>> list = new ArrayList<Freq<T>>(freqMap.size());
        for (T key : freqMap.keySet()) {
            Freq<T> freq = new Freq<>(key, freqMap.get(key));
            list.add(freq);
        }
        if (descending) {
            Collections.sort(list, (o1, o2) -> Integer.compare(o2.count, o1.count));
        } else {
            Collections.sort(list, (o1, o2) -> Integer.compare(o1.count, o2.count));
        }
        return list;
    }

    public static class Freq<T> {
        T value;
        int count;

        public Freq(T value, int count) {
            super();
            this.value = value;
            this.count = count;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(value).append('\t').append(count);
            return sb.toString();
        }

        public T getValue() {
            return value;
        }

        public int getCount() {
            return count;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        orderKeys();

        sb.append("Value\tFrequency\n");
        sb.append("------------------\n");
        for (Comparable<T> key : sortedKeys) {
            int count = freqMap.get(key);
            sb.append(key).append('\t').append(count).append("\n");
        }
        return sb.toString();
    }

    public List<Comparable<T>> getSortedKeys() {
        if (sortedKeys == null)
            orderKeys();
        return sortedKeys;
    }

    public Iterator<T> getIterator() {
        return freqMap.keySet().iterator();
    }

}
