package ahocorasick;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generates reproducible test data (fixed seeds) for the experiments. */
public final class DataGenerator {

    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    private DataGenerator() {
    }

    public static String randomText(int length, Random rnd) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Half of the patterns are cut out of the text (so they surely occur),
     * the other half are random words that most likely do not occur.
     * Lengths are uniform in [minLen, maxLen].
     */
    public static List<String> patterns(int count, String text, int minLen, int maxLen, Random rnd) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int len = minLen + rnd.nextInt(maxLen - minLen + 1);
            if (i % 2 == 0 && text.length() >= len) {
                int from = rnd.nextInt(text.length() - len + 1);
                result.add(text.substring(from, from + len));
            } else {
                result.add(randomText(len, rnd));
            }
        }
        return result;
    }
}
