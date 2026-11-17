package me.whereareiam.anvil.capability.packets.recording;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreamAlignmentTest {
	@Test
	void pairsEqualSequencesStepByStep() {
		assertEquals(List.of(step(0, 0), step(1, 1)), StreamAlignment.align(List.of("a", "b"), List.of("a", "b")));
	}

	@Test
	void findsTheOneFrameOnlyOneSideHas() {
		assertEquals(List.of(step(0, 0), step(1, -1), step(2, 1)),
				StreamAlignment.align(List.of("a", "b", "c"), List.of("a", "c")));
		assertEquals(List.of(step(0, 0), step(-1, 1), step(1, 2)),
				StreamAlignment.align(List.of("a", "c"), List.of("a", "b", "c")));
	}

	@Test
	void reportsAReorderingAsOneFrameMovedNotAsEveryFrameAfterIt() {
		List<StreamAlignment.Step> steps = StreamAlignment.align(List.of("chunk", "repair", "x", "y"),
				List.of("repair", "chunk", "x", "y"));

		assertEquals(2, steps.stream().filter(step -> step.expected() < 0 || step.actual() < 0).count());
		assertEquals(step(2, 2), steps.get(steps.size() - 2));
	}

	@Test
	void givesUpOnStreamsThatDifferTooMuch() {
		List<String> left = new ArrayList<>(Collections.nCopies(StreamAlignment.MOST_DIFFERENCES, "a"));
		List<String> right = new ArrayList<>(Collections.nCopies(StreamAlignment.MOST_DIFFERENCES, "b"));

		assertNull(StreamAlignment.align(left, right));
	}

	private static StreamAlignment.Step step(int expected, int actual) {
		return new StreamAlignment.Step(expected, actual);
	}
}
