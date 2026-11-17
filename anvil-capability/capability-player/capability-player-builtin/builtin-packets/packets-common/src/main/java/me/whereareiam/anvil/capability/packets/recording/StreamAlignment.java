package me.whereareiam.anvil.capability.packets.recording;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aligns two sequences of frame keys with the fewest frames only one side has, by Myers' difference algorithm, so that
 * one missing or extra frame is one difference instead of shifting every frame after it. The work grows with the
 * number of differences; past {@value #MOST_DIFFERENCES} differences the streams are told apart only up to the first
 * one.
 */
final class StreamAlignment {
	/**
	 * The most differences an alignment looks for.
	 */
	static final int MOST_DIFFERENCES = 1024;

	private StreamAlignment() {
	}

	/**
	 * Aligns two key sequences.
	 *
	 * @return the steps from the start of both sequences to their ends, or {@code null} when they differ in more than
	 * {@value #MOST_DIFFERENCES} places
	 */
	static List<Step> align(@NotNull List<String> expected, @NotNull List<String> actual) {
		int n = expected.size();
		int m = actual.size();
		int max = Math.min(n + m, MOST_DIFFERENCES);
		int offset = max + 1;
		int[] furthest = new int[2 * max + 3];
		List<int[]> trace = new ArrayList<>();
		for (int d = 0; d <= max; d++) {
			trace.add(furthest.clone());
			for (int k = -d; k <= d; k += 2) {
				int x = k == -d || (k != d && furthest[offset + k - 1] < furthest[offset + k + 1])
						? furthest[offset + k + 1]
						: furthest[offset + k - 1] + 1;
				int y = x - k;
				while (x < n && y < m && expected.get(x).equals(actual.get(y))) {
					x++;
					y++;
				}
				furthest[offset + k] = x;
				if (x >= n && y >= m) return steps(trace, offset, n, m, d);
			}
		}

		return null;
	}

	private static List<Step> steps(List<int[]> trace, int offset, int n, int m, int differences) {
		List<Step> steps = new ArrayList<>();
		int x = n;
		int y = m;
		for (int d = differences; d > 0; d--) {
			int[] furthest = trace.get(d);
			int k = x - y;
			int previousK = k == -d || (k != d && furthest[offset + k - 1] < furthest[offset + k + 1]) ? k + 1 : k - 1;
			int previousX = furthest[offset + previousK];
			int previousY = previousX - previousK;
			while (x > previousX && y > previousY) steps.add(new Step(--x, --y));
			if (x == previousX) steps.add(new Step(-1, --y));
			else steps.add(new Step(--x, -1));
		}
		while (x > 0 && y > 0) steps.add(new Step(--x, --y));
		Collections.reverse(steps);

		return steps;
	}

	/**
	 * One step of an alignment: two positions that pair up, or one position and {@code -1} for a frame only one side
	 * has.
	 *
	 * @param expected the position in the expected sequence, or {@code -1}
	 * @param actual the position in the actual sequence, or {@code -1}
	 */
	record Step(int expected, int actual) {
	}
}
