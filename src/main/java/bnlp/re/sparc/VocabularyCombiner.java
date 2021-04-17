package bnlp.re.sparc;

import bnlp.common.CharSetEncoding;
import bnlp.re.util.FileUtils;

import java.io.IOException;
import java.util.*;

/**
 * Created by bozyurt on 10/27/20.
 */
public class VocabularyCombiner {

    public static void main(String[] args) throws IOException {
        String HOME_DIR = System.getProperty("user.home");
        String dataDir = HOME_DIR + "/dev/java/bnlp-re/data/sparc";
        String[] lines = FileUtils.readLines(dataDir + "/sparc_vocabulary.txt", true, CharSetEncoding.UTF8);
        Set<String> set = new HashSet<>();
        for(String line : lines) {
            set.add(line.trim().toLowerCase());
        }
        List<String> vocabulary = new ArrayList<>(Arrays.asList(lines));
        String[] keatsStructures = FileUtils.readLines(dataDir + "/sparc_vocabulary.txt", true, CharSetEncoding.UTF8);
        for(String structure : keatsStructures) {
            if (!set.contains(structure.toLowerCase())) {
                System.out.println("added " + structure);
                vocabulary.add(structure.toLowerCase());
            }
        }

        FileUtils.saveList(vocabulary, "/tmp/sparc_vocabulary_combined.txt", CharSetEncoding.UTF8);
        System.out.println("done.");
    }
}
