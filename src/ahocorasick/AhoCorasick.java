package ahocorasick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Aho-Corasick automaton for searching many patterns in one pass over the text.
 *
 * Nodes are stored in plain arrays (node 0 is the root). The alphabet is
 * compressed to the characters that actually appear in the patterns, so a
 * transition row has only {@code sigma} cells instead of 65536.
 */
public final class AhoCorasick {

    /** Called for every occurrence found during the search. */
    @FunctionalInterface
    public interface MatchHandler {
        void onMatch(int patternIndex, int start);
    }

    private static final int ROOT = 0;
    private static final int NONE = -1;

    private final String[] patterns;

    // alphabet compression: char -> column in the transition table (-1 = not in any pattern)
    private int[] charIndex;
    private int sigma;

    // trie
    private int[][] next;      // next[node][c] = child, or NONE
    private int[][] outputs;   // ids of patterns that end exactly in this node
    private int nodeCount;

    // links built by BFS
    private int[] fail;        // longest proper suffix of the node's string that is also in the trie
    private int[] outLink;     // nearest node on the fail chain that ends some pattern (NONE if there is none)

    public AhoCorasick(List<String> patterns) {
        this(patterns.toArray(new String[0]));
    }

    public AhoCorasick(String... patterns) {
        if (patterns.length == 0) {
            throw new IllegalArgumentException("need at least one pattern");
        }
        this.patterns = patterns.clone();
        buildAlphabet();
        buildTrie();
        buildFailureLinks();
    }

    // ------------------------------------------------------------------ build

    private void buildAlphabet() {
        int maxChar = 0;
        for (String p : patterns) {
            if (p == null || p.isEmpty()) {
                throw new IllegalArgumentException("patterns must be non-empty");
            }
            for (int i = 0; i < p.length(); i++) {
                maxChar = Math.max(maxChar, p.charAt(i));
            }
        }
        charIndex = new int[maxChar + 1];
        Arrays.fill(charIndex, NONE);
        sigma = 0;
        for (String p : patterns) {
            for (int i = 0; i < p.length(); i++) {
                char ch = p.charAt(i);
                if (charIndex[ch] == NONE) {
                    charIndex[ch] = sigma++;
                }
            }
        }
    }

    /** Step 1: insert every pattern into the trie. */
    private void buildTrie() {
        int maxNodes = 1;
        for (String p : patterns) {
            maxNodes += p.length();
        }
        next = new int[maxNodes][];
        List<List<Integer>> ends = new ArrayList<>();

        next[ROOT] = emptyRow();
        ends.add(new ArrayList<>());
        nodeCount = 1;

        for (int id = 0; id < patterns.length; id++) {
            String p = patterns[id];
            int v = ROOT;
            for (int i = 0; i < p.length(); i++) {
                int c = charIndex[p.charAt(i)];
                if (next[v][c] == NONE) {
                    next[v][c] = nodeCount;
                    next[nodeCount] = emptyRow();
                    ends.add(new ArrayList<>());
                    nodeCount++;
                }
                v = next[v][c];
            }
            ends.get(v).add(id);
        }

        next = Arrays.copyOf(next, nodeCount);
        outputs = new int[nodeCount][];
        for (int v = 0; v < nodeCount; v++) {
            outputs[v] = ends.get(v).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /**
     * Step 2: failure links and output links, level by level (BFS).
     * When we process a node, all shallower nodes already have correct links,
     * and the fail target is always shallower, so one pass is enough.
     */
    private void buildFailureLinks() {
        fail = new int[nodeCount];
        outLink = new int[nodeCount];
        Arrays.fill(outLink, NONE);

        int[] queue = new int[nodeCount];
        int head = 0, tail = 0;

        // depth-1 nodes always fail to the root
        for (int c = 0; c < sigma; c++) {
            int u = next[ROOT][c];
            if (u != NONE) {
                fail[u] = ROOT;
                queue[tail++] = u;
            }
        }

        while (head < tail) {
            int v = queue[head++];
            for (int c = 0; c < sigma; c++) {
                int u = next[v][c];
                if (u == NONE) {
                    continue;
                }
                // walk the fail chain of the parent until someone can continue with c
                int f = fail[v];
                while (f != ROOT && next[f][c] == NONE) {
                    f = fail[f];
                }
                int target = next[f][c];
                fail[u] = (target == NONE) ? ROOT : target;

                // output link = closest terminal node on the fail chain
                int fu = fail[u];
                outLink[u] = outputs[fu].length > 0 ? fu : outLink[fu];

                queue[tail++] = u;
            }
        }
    }

    private int[] emptyRow() {
        int[] row = new int[sigma];
        Arrays.fill(row, NONE);
        return row;
    }

    // ----------------------------------------------------------------- search

    /** Step 3: one left-to-right pass over the text, reporting every occurrence. */
    public void search(String text, MatchHandler handler) {
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int c = ch < charIndex.length ? charIndex[ch] : NONE;
            if (c == NONE) {           // character never appears in any pattern
                state = ROOT;
                continue;
            }
            while (state != ROOT && next[state][c] == NONE) {
                state = fail[state];
            }
            int to = next[state][c];
            state = (to == NONE) ? ROOT : to;

            // the current node plus every terminal node reachable by output links
            for (int t = state; t != NONE; t = outLink[t]) {
                for (int id : outputs[t]) {
                    handler.onMatch(id, i - patterns[id].length() + 1);
                }
            }
        }
    }

    public List<Match> findAll(String text) {
        List<Match> result = new ArrayList<>();
        search(text, (id, start) -> result.add(new Match(id, start)));
        return result;
    }

    /** Same pass as {@link #search}, but only counts occurrences (used in benchmarks). */
    public long countMatches(String text) {
        long count = 0;
        int state = ROOT;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int c = ch < charIndex.length ? charIndex[ch] : NONE;
            if (c == NONE) {
                state = ROOT;
                continue;
            }
            while (state != ROOT && next[state][c] == NONE) {
                state = fail[state];
            }
            int to = next[state][c];
            state = (to == NONE) ? ROOT : to;
            for (int t = state; t != NONE; t = outLink[t]) {
                count += outputs[t].length;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- getters

    public int nodeCount() {
        return nodeCount;
    }

    public int alphabetSize() {
        return sigma;
    }

    public String pattern(int id) {
        return patterns[id];
    }

    /** Exposed for tests: fail link of the node reached by reading {@code s} from the root. */
    int failOf(String s) {
        return fail[nodeOf(s)];
    }

    /** Exposed for tests: node reached by reading {@code s} from the root, or -1. */
    int nodeOf(String s) {
        int v = ROOT;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int c = ch < charIndex.length ? charIndex[ch] : NONE;
            if (c == NONE || next[v][c] == NONE) {
                return NONE;
            }
            v = next[v][c];
        }
        return v;
    }
}
