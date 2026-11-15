package ahocorasick;

/** One occurrence: pattern number {@code patternIndex} starts at position {@code start} of the text. */
public record Match(int patternIndex, int start) implements Comparable<Match> {

    @Override
    public int compareTo(Match o) {
        if (start != o.start) {
            return Integer.compare(start, o.start);
        }
        return Integer.compare(patternIndex, o.patternIndex);
    }
}
