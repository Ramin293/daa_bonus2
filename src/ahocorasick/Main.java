package ahocorasick;

import java.util.Collections;
import java.util.List;

/** Small demo: the classic example from the original paper. */
public class Main {

    public static void main(String[] args) {
        String[] patterns = {"he", "she", "his", "hers"};
        String text = "ushers";

        AhoCorasick ac = new AhoCorasick(patterns);
        List<Match> matches = ac.findAll(text);
        Collections.sort(matches);

        System.out.println("Text:     " + text);
        System.out.println("Patterns: " + String.join(", ", patterns));
        System.out.println("Trie nodes: " + ac.nodeCount());
        System.out.println();
        for (Match m : matches) {
            String p = ac.pattern(m.patternIndex());
            System.out.printf("  \"%s\" at [%d..%d]%n", p, m.start(), m.start() + p.length() - 1);
        }
    }
}
