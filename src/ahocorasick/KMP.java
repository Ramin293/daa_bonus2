package ahocorasick;

import java.util.ArrayList;
import java.util.List;

/** Knuth-Morris-Pratt for a single pattern. Used as the baseline: run it once per pattern. */
public final class KMP {

    private KMP() {
    }

    /** pi[i] = length of the longest proper prefix of p[0..i] that is also its suffix. */
    public static int[] prefixFunction(String p) {
        int[] pi = new int[p.length()];
        int k = 0;
        for (int i = 1; i < p.length(); i++) {
            while (k > 0 && p.charAt(i) != p.charAt(k)) {
                k = pi[k - 1];
            }
            if (p.charAt(i) == p.charAt(k)) {
                k++;
            }
            pi[i] = k;
        }
        return pi;
    }

    public static List<Integer> findAll(String text, String pattern) {
        List<Integer> starts = new ArrayList<>();
        int[] pi = prefixFunction(pattern);
        int m = pattern.length();
        int k = 0;
        for (int i = 0; i < text.length(); i++) {
            while (k > 0 && text.charAt(i) != pattern.charAt(k)) {
                k = pi[k - 1];
            }
            if (text.charAt(i) == pattern.charAt(k)) {
                k++;
            }
            if (k == m) {
                starts.add(i - m + 1);
                k = pi[k - 1];
            }
        }
        return starts;
    }

    public static long count(String text, String pattern) {
        int[] pi = prefixFunction(pattern);
        int m = pattern.length();
        long count = 0;
        int k = 0;
        for (int i = 0; i < text.length(); i++) {
            while (k > 0 && text.charAt(i) != pattern.charAt(k)) {
                k = pi[k - 1];
            }
            if (text.charAt(i) == pattern.charAt(k)) {
                k++;
            }
            if (k == m) {
                count++;
                k = pi[k - 1];
            }
        }
        return count;
    }

    /** The "naive multi-pattern" approach: k independent KMP runs over the same text. */
    public static long countAll(String text, List<String> patterns) {
        long total = 0;
        for (String p : patterns) {
            total += count(text, p);
        }
        return total;
    }
}
