package ahocorasick;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Runs Aho-Corasick and "KMP for every pattern" on all combinations of
 * pattern count {10, 100, 1000} and text length {1000, 10000, 100000}.
 * Each experiment is repeated 5 times (after a few warm-up runs for the JIT)
 * and the average time is reported. Results also go to results/results.csv.
 */
public class Benchmark {

    private static final int[] PATTERN_COUNTS = {10, 100, 1_000};
    private static final int[] TEXT_LENGTHS = {1_000, 10_000, 100_000};
    private static final int RUNS = 5;
    private static final int WARMUP = 10;
    private static final int MIN_LEN = 3;
    private static final int MAX_LEN = 10;

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);
        Path out = Path.of(args.length > 0 ? args[0] : "results/results.csv");
        Files.createDirectories(out.toAbsolutePath().getParent());

        globalWarmUp();

        System.out.printf("Aho-Corasick vs KMP-per-pattern, %d runs per case (+%d warm-up), times in ms%n%n",
                RUNS, WARMUP);
        System.out.println("| Patterns | Text length | Trie nodes | AC build | AC search | AC total | KMP total | KMP / AC | Matches |");
        System.out.println("|---:|---:|---:|---:|---:|---:|---:|---:|---:|");

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out))) {
            csv.println("patterns,text_length,trie_nodes,ac_build_ms,ac_search_ms,ac_total_ms,kmp_total_ms,speedup,matches");

            for (int k : PATTERN_COUNTS) {
                for (int n : TEXT_LENGTHS) {
                    Random rnd = new Random(31L * k + n);
                    String text = DataGenerator.randomText(n, rnd);
                    List<String> patterns = DataGenerator.patterns(k, text, MIN_LEN, MAX_LEN, rnd);

                    for (int w = 0; w < WARMUP; w++) {
                        new AhoCorasick(patterns).countMatches(text);
                        KMP.countAll(text, patterns);
                    }

                    long buildNs = 0, searchNs = 0, kmpNs = 0;
                    long acMatches = 0, kmpMatches = 0;
                    int nodes = 0;

                    for (int r = 0; r < RUNS; r++) {
                        long t0 = System.nanoTime();
                        AhoCorasick ac = new AhoCorasick(patterns);
                        long t1 = System.nanoTime();
                        acMatches = ac.countMatches(text);
                        long t2 = System.nanoTime();
                        buildNs += t1 - t0;
                        searchNs += t2 - t1;
                        nodes = ac.nodeCount();

                        long t3 = System.nanoTime();
                        kmpMatches = KMP.countAll(text, patterns);
                        long t4 = System.nanoTime();
                        kmpNs += t4 - t3;
                    }

                    if (acMatches != kmpMatches) {
                        throw new IllegalStateException("results differ: AC=" + acMatches + ", KMP=" + kmpMatches);
                    }

                    double build = ms(buildNs / RUNS);
                    double search = ms(searchNs / RUNS);
                    double total = build + search;
                    double kmp = ms(kmpNs / RUNS);
                    double speedup = kmp / total;

                    System.out.printf("| %d | %d | %d | %.3f | %.3f | %.3f | %.3f | %.1fx | %d |%n",
                            k, n, nodes, build, search, total, kmp, speedup, acMatches);
                    csv.printf("%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.2f,%d%n",
                            k, n, nodes, build, search, total, kmp, speedup, acMatches);
                }
            }
        }
        System.out.println();
        System.out.println("Saved to " + out);
    }

    /**
     * Without this the very first (smallest) case gets measured while the JVM
     * is still interpreting the code, and looks much slower than it really is.
     */
    private static void globalWarmUp() {
        Random rnd = new Random(1);
        String text = DataGenerator.randomText(50_000, rnd);
        List<String> patterns = DataGenerator.patterns(200, text, MIN_LEN, MAX_LEN, rnd);
        for (int i = 0; i < 20; i++) {
            new AhoCorasick(patterns).countMatches(text);
            KMP.countAll(text, patterns);
        }
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }
}
