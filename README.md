# Aho-Corasick in Java

Bonus assignment #2 for Design and Analysis of Algorithms.

The task: implement Aho-Corasick for multi-pattern search, measure it on different numbers of patterns and text sizes, and compare it with the obvious alternative of running KMP once for every pattern.

Everything is plain Java 17, no Maven or external libraries, so it compiles with just `javac`.

## What's inside

```
src/ahocorasick/
    AhoCorasick.java     the algorithm: trie, failure links, output links, search
    KMP.java             prefix function + KMP search (the baseline)
    Match.java           (patternIndex, start) pair
    DataGenerator.java   random text / patterns with fixed seeds
    Benchmark.java       the experiments from the task
    Main.java            small demo on the classic "ushers" example
test/ahocorasick/
    AhoCorasickTest.java tests (no JUnit, just a main method)
results/
    results.csv          numbers from my run
```

## How to run

```bash
javac -encoding UTF-8 -d out src/ahocorasick/*.java test/ahocorasick/*.java

java -cp out ahocorasick.Main             # demo
java -cp out ahocorasick.AhoCorasickTest  # tests
java -cp out ahocorasick.Benchmark        # experiments, writes results/results.csv
```

The demo searches for `he, she, his, hers` in `ushers` and prints:

```
  "she" at [1..3]
  "he" at [2..3]
  "hers" at [2..5]
```

## How I implemented it

I split it into the same three steps we had in the lecture.

**1. Trie.** Every pattern is inserted into a trie character by character. If a node ends a pattern, I store the pattern's index in that node (a list, because the same pattern can be given twice). Nodes are just numbers, and transitions are stored in an `int[][] next` table where `-1` means "no edge". To keep the table small I first collect all characters that appear in the patterns and give each one a column number. So for lowercase English text a row has at most 26 cells, not 65536.

**2. Failure links.** For a node that represents a string `s`, the failure link points to the node of the longest proper suffix of `s` that is also a path in the trie. I compute them with BFS from the root:

- children of the root always fail to the root;
- for a node `u = next[v][c]`, I start from `fail[v]` and keep following failure links until I find a node that has an edge by `c` (or reach the root). That edge is `fail[u]`.

BFS order matters here. The failure target is always shallower than the node itself, so when we get to `u`, everything we need is already computed.

**3. Output links.** A node can be in the middle of a long pattern while a shorter pattern ends at the same position. For example, while reading `she` we are also at the end of `he`. To not miss those, each node has an `outLink` pointing to the nearest node on its failure chain where some pattern ends. During search I report the patterns of the current node and then jump along output links. This way we never walk through failure-chain nodes that don't contain any match.

**4. Search.** One pass over the text. For each character: if there is an edge, follow it. If not, follow failure links until there is one or we are back at the root. Then report everything reachable through output links. Characters that don't occur in any pattern just reset the state to the root.

There are two search methods: `findAll(text)` returns a list of matches, and `countMatches(text)` only counts them. In the benchmark I used counting for both algorithms, so that creating millions of `Match` objects doesn't affect the timing.

## Testing

`AhoCorasickTest` checks:

- the classic example from the original paper (`he, she, his, hers` in `ushers`), including the exact failure links (`fail(she) = he`, `fail(hers) = s`, ...);
- overlapping matches (`a, aa, aaa` in `aaaa` gives 9 matches);
- a pattern that is a suffix of another pattern (found only thanks to output links);
- duplicate patterns, a pattern longer than the text, an empty text, characters that are not in any pattern (including a Cyrillic letter);
- that an empty pattern or an empty pattern list throws an exception;
- KMP on its own (prefix function and overlapping occurrences);
- 2000 random small cases on an alphabet of 2-4 letters, compared with brute force and with KMP. A small alphabet gives a lot of overlaps, which is where bugs usually show up.

Current result: `22 passed, 0 failed`.

## Experiments

Setup:

- text: random lowercase English letters, length 1,000 / 10,000 / 100,000;
- patterns: 10 / 100 / 1,000, length 3 to 10. Half of them are cut out of the text (so they definitely occur), the other half are random and almost never occur;
- every case runs 5 times and the average is shown, timed with `System.nanoTime()`. Before that each case runs 10 times without measuring, plus one global warm-up at the start. Without it, the first case was measured while the JVM was still interpreting the code and looked several times slower than it really was;
- "AC total" = building the automaton + searching. For KMP the time includes computing the prefix function for every pattern;
- the benchmark also checks that both algorithms found the same number of matches. If not, it throws an exception.

Machine: Intel Core Ultra 9 275HX, 32 GB RAM, Windows 11, OpenJDK 17.

All times are in milliseconds.

| Patterns | Text length | Trie nodes | AC build | AC search | AC total | KMP total | KMP / AC | Matches |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1,000 | 62 | 0.016 | 0.008 | 0.024 | 0.010 | 0.4x | 5 |
| 10 | 10,000 | 63 | 0.017 | 0.105 | 0.122 | 0.113 | 0.9x | 7 |
| 10 | 100,000 | 73 | 0.021 | 0.463 | 0.485 | 1.129 | 2.3x | 5 |
| 100 | 1,000 | 572 | 0.103 | 0.004 | 0.107 | 0.106 | 1.0x | 51 |
| 100 | 10,000 | 566 | 0.115 | 0.039 | 0.155 | 1.105 | 7.1x | 66 |
| 100 | 100,000 | 520 | 0.407 | 0.380 | 0.787 | 11.193 | 14.2x | 158 |
| 1,000 | 1,000 | 4,707 | 0.636 | 0.012 | 0.647 | 1.072 | 1.7x | 505 |
| 1,000 | 10,000 | 5,107 | 1.049 | 0.082 | 1.131 | 11.167 | 9.9x | 564 |
| 1,000 | 100,000 | 5,143 | 0.691 | 0.709 | 1.400 | 113.154 | 80.8x | 1201 |

"KMP / AC" shows how many times Aho-Corasick was faster. Below 1 means KMP won.

The numbers are a bit noisy for the tiny cases (a few microseconds), so I wouldn't read too much into the third digit there. I ran the benchmark several times, and the big cases were stable within a few percent.

### What the results show

**KMP grows exactly like k · n.** Look at the KMP column: 0.11 ms, 1.1 ms, 11 ms, 113 ms. Every time either the number of patterns or the text length grows 10x, the time grows 10x too. That makes sense, because we scan the whole text once per pattern, so it's about 1 ns per character per pattern on this machine.

**Aho-Corasick search barely depends on the number of patterns.** For a 100,000-character text the search took 0.46 / 0.38 / 0.71 ms for 10 / 100 / 1000 patterns. The number of patterns went up 100 times, and the search time stayed about the same. That's the whole point of the algorithm: the text is read once no matter how many patterns there are. It's slightly slower with 1000 patterns, probably because the transition table (about 5000 × 26 ints) no longer fits in the fastest cache, and there are more matches to report.

I was surprised that 10 patterns were a bit slower than 100. My guess is that with only 10 patterns, most text letters aren't in the alphabet at all, so the "unknown character, go to root" branch is taken randomly, and the CPU can't predict it well. I didn't dig deeper into this.

**For small inputs Aho-Corasick isn't worth it.** With 10 patterns and 1000 characters KMP was faster (0.010 vs 0.024 ms). Building a trie and running BFS costs more than just scanning a short text 10 times. With 1000 patterns, building the automaton takes around 0.6-1 ms, which is longer than the whole search on 100,000 characters. So when the text is short, preprocessing takes most of the time.

**For big inputs the difference is huge.** 1000 patterns on 100,000 characters: 1.4 ms vs 113 ms, about 80 times faster. In a real use case like a virus scanner or a word filter, where there are thousands of patterns and lots of text, this matters a lot.

## Complexity analysis

Notation: `n` is the text length, `k` the number of patterns, `M` the total length of all patterns, `σ` the alphabet size, `z` the number of matches found.

### Aho-Corasick

| Step | Time | Memory |
|---|---|---|
| Building the trie | O(M) node visits, O(M · σ) with my table (every new node gets a row of σ cells) | O(M · σ) |
| Failure + output links | O(M · σ) | O(M) |
| Search | O(n + z) | O(1) extra |
| **Total** | **O(M · σ + n + z)** | **O(M · σ)** |

Some details:

- The trie has at most `M + 1` nodes, because each pattern character creates at most one new node. In practice there are fewer because of shared prefixes: in the table, 1000 patterns with total length about 6500 produced around 5100 nodes.
- Why building failure links is linear: for a single pattern, think of the depth of the failure link as we go down its path. Every step down increases it by at most 1, and every jump along a failure link decreases it by at least 1. So the total number of jumps along one pattern is at most its length, and over all patterns at most `M`. The `σ` factor comes only from the BFS checking every cell of each row.
- Why search is O(n + z): the same argument works on the text. The state depth goes up by at most 1 per character and every failure jump makes it smaller, so all jumps together are at most `n`. Output links only visit nodes that really are matches, so reporting costs exactly `z`. Without output links we would have to walk the whole failure chain at every position, and that could get much slower.
- If I used a `HashMap` per node instead of a table, memory and build time would be O(M), but each step in the search would be a hash lookup instead of an array access. For a small alphabet the table is faster and simpler.
- `z` can be as large as about `n · k` (for example, patterns `a, aa, aaa, ...` on text `aaaa...`). No algorithm can do better than this if it has to report every match.

### KMP for each pattern separately

- Prefix function for pattern `i`: O(m_i), in total O(M).
- Each search: O(n), done `k` times, so O(k · n).
- **Total: O(M + k · n)**, memory O(max m_i) for one prefix function at a time.

So the difference is `n + z` vs `k · n`. For one pattern they are basically the same, but for 1000 patterns KMP reads the text 1000 times.

## Why the trie and the failure links matter

**The trie** puts all patterns into one structure. Patterns with the same beginning share nodes, so when we read the text we move through all patterns at the same time. Being in a node means "the last few characters I've read match this prefix, and maybe some patterns that start with it."

**Failure links** are the same idea as the prefix function in KMP, but for a whole set of patterns. When the next character doesn't continue the current prefix, we don't go back in the text and start over. We jump to the longest suffix of what we've already read that is still a prefix of *some* pattern (not necessarily the same one). Because of that, the text pointer only moves forward, and every character is read exactly once.

**Output links** handle patterns that are hidden inside other patterns as suffixes, like `he` inside `she`. Without them we'd either miss those matches or have to walk the whole failure chain at every position.

That's why Aho-Corasick works well for searching many patterns: the cost of reading the text doesn't depend on how many patterns there are. All patterns are checked in the same single pass, and the extra work is only proportional to how many matches there actually are.

## Aho-Corasick vs KMP: pros and cons

**Aho-Corasick**

- \+ one pass over the text for any number of patterns, so it's great when there are hundreds or thousands of patterns;
- \+ works on a stream, since it never needs to go back in the text;
- \+ finds all overlapping matches of all patterns, and the reporting cost is proportional to the number of matches;
- − you have to build the automaton first, and for short texts that takes longer than the search itself;
- − needs more memory: O(M · σ) for a table, which gets bad for large alphabets like full Unicode (then you need maps and it gets slower);
- − the set of patterns is fixed: to add or remove a pattern, you have to rebuild the failure links;
- − harder to write and debug. I had to think carefully about output links and the BFS order.

**KMP for each pattern**

- \+ very simple, only a few lines of code, and uses O(m) memory;
- \+ patterns are independent, so adding one more is free and you can even process them in parallel;
- \+ for one or a few patterns, or a short text, it's as fast or faster (see the first rows of the table);
- − reads the text `k` times, so the time grows linearly with the number of patterns. With 1000 patterns it was 80 times slower;
- − needs the whole text in memory (or reading it `k` times), so it doesn't fit streaming well.

**Conclusion:** if there are only a few patterns, I'd just use KMP (or even the built-in `indexOf`). If there are many patterns or the text is long, Aho-Corasick is the right choice, and the experiments match the theory pretty closely.
