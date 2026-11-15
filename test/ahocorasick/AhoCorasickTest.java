package ahocorasick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Plain-Java tests (no JUnit needed). Run with:
 *   java -cp out ahocorasick.AhoCorasickTest
 * Exits with code 1 if anything fails.
 */
public class AhoCorasickTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        classicExample();
        failureLinksOfClassicExample();
        overlappingMatches();
        patternInsidePattern();
        duplicatePatterns();
        noMatches();
        patternLongerThanText();
        charactersOutsideAlphabet();
        singleCharacterPatterns();
        emptyTextAndBadInput();
        kmpBasics();
        randomCrossCheck();

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- test cases

    static void classicExample() {
        AhoCorasick ac = new AhoCorasick("he", "she", "his", "hers");
        List<Match> got = sorted(ac.findAll("ushers"));
        List<Match> want = List.of(new Match(1, 1), new Match(0, 2), new Match(3, 2));
        check("classic he/she/his/hers in 'ushers'", want, got);
        check("classic: node count", 10, ac.nodeCount());
    }

    static void failureLinksOfClassicExample() {
        AhoCorasick ac = new AhoCorasick("he", "she", "his", "hers");
        check("fail(she) = he", ac.nodeOf("he"), ac.failOf("she"));
        check("fail(sh) = h", ac.nodeOf("h"), ac.failOf("sh"));
        check("fail(hers) = s", ac.nodeOf("s"), ac.failOf("hers"));
        check("fail(his) = s", ac.nodeOf("s"), ac.failOf("his"));
        check("fail(he) = root", 0, ac.failOf("he"));
    }

    static void overlappingMatches() {
        AhoCorasick ac = new AhoCorasick("a", "aa", "aaa");
        // "aaaa": a x4, aa x3, aaa x2
        check("overlapping a/aa/aaa in 'aaaa'", 9L, ac.countMatches("aaaa"));
        check("overlapping matches equal naive", naive("aaaa", List.of("a", "aa", "aaa")), sorted(ac.findAll("aaaa")));
    }

    static void patternInsidePattern() {
        // "c" and "bc" are only reachable through output links while we stand in "abc"
        List<String> ps = List.of("abcd", "bc", "c");
        AhoCorasick ac = new AhoCorasick(ps);
        check("suffix patterns via output links", naive("xabcy", ps), sorted(ac.findAll("xabcy")));
    }

    static void duplicatePatterns() {
        AhoCorasick ac = new AhoCorasick("ab", "ab", "b");
        List<Match> want = List.of(new Match(0, 0), new Match(1, 0), new Match(2, 1));
        check("duplicate patterns reported separately", want, sorted(ac.findAll("ab")));
    }

    static void noMatches() {
        AhoCorasick ac = new AhoCorasick("xyz", "qq");
        check("no matches", 0L, ac.countMatches("abcabcabc"));
    }

    static void patternLongerThanText() {
        AhoCorasick ac = new AhoCorasick("abcdefgh");
        check("pattern longer than text", 0L, ac.countMatches("abc"));
    }

    static void charactersOutsideAlphabet() {
        AhoCorasick ac = new AhoCorasick("ab", "b");
        // '-' and 'Я' are not in any pattern: the automaton must reset to the root
        List<Match> want = List.of(new Match(0, 0), new Match(1, 1), new Match(1, 4), new Match(0, 6), new Match(1, 7));
        check("unknown characters reset the state", want, sorted(ac.findAll("ab-Яbaab")));
    }

    static void singleCharacterPatterns() {
        List<String> ps = List.of("a", "b", "c");
        AhoCorasick ac = new AhoCorasick(ps);
        check("single characters", naive("abcabcxx", ps), sorted(ac.findAll("abcabcxx")));
    }

    static void emptyTextAndBadInput() {
        AhoCorasick ac = new AhoCorasick("a");
        check("empty text", 0L, ac.countMatches(""));
        check("empty pattern rejected", true, throwsIAE(() -> new AhoCorasick("a", "")));
        check("no patterns rejected", true, throwsIAE(() -> new AhoCorasick(new String[0])));
    }

    static void kmpBasics() {
        check("prefix function of 'aabaaab'", Arrays.toString(new int[]{0, 1, 0, 1, 2, 2, 3}),
                Arrays.toString(KMP.prefixFunction("aabaaab")));
        check("KMP overlapping 'aba' in 'ababa'", List.of(0, 2), KMP.findAll("ababa", "aba"));
        check("KMP count", 3L, KMP.count("aaaa", "aa"));
    }

    /** Thousands of random small cases compared against brute force and KMP. */
    static void randomCrossCheck() {
        Random rnd = new Random(2025);
        int bad = 0;
        for (int iter = 0; iter < 2000; iter++) {
            int sigma = 2 + rnd.nextInt(3);              // tiny alphabet => lots of overlaps
            String text = randomString(rnd.nextInt(60), sigma, rnd);
            List<String> ps = new ArrayList<>();
            int k = 1 + rnd.nextInt(8);
            for (int i = 0; i < k; i++) {
                ps.add(randomString(1 + rnd.nextInt(5), sigma, rnd));
            }
            AhoCorasick ac = new AhoCorasick(ps);
            List<Match> got = sorted(ac.findAll(text));
            List<Match> want = naive(text, ps);
            if (!got.equals(want) || ac.countMatches(text) != want.size()
                    || KMP.countAll(text, ps) != want.size()) {
                bad++;
                if (bad <= 3) {
                    System.out.println("  mismatch: text=" + text + " patterns=" + ps);
                }
            }
        }
        check("2000 random cases vs brute force", 0, bad);
    }

    // ---------------------------------------------------------------- helpers

    static List<Match> naive(String text, List<String> patterns) {
        List<Match> res = new ArrayList<>();
        for (int id = 0; id < patterns.size(); id++) {
            String p = patterns.get(id);
            for (int i = 0; i + p.length() <= text.length(); i++) {
                if (text.startsWith(p, i)) {
                    res.add(new Match(id, i));
                }
            }
        }
        Collections.sort(res);
        return res;
    }

    static String randomString(int len, int sigma, Random rnd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + rnd.nextInt(sigma)));
        }
        return sb.toString();
    }

    static List<Match> sorted(List<Match> list) {
        List<Match> copy = new ArrayList<>(list);
        Collections.sort(copy);
        return copy;
    }

    static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    static void check(String name, Object expected, Object actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("[ OK ] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name + "\n       expected: " + expected + "\n       actual:   " + actual);
        }
    }
}
