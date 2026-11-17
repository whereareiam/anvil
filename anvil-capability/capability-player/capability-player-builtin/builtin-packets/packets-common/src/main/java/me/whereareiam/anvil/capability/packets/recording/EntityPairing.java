package me.whereareiam.anvil.capability.packets.recording;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Pairs the entity IDs of two packet streams by the order they first appear in: the first time the expected stream
 * names an entity where the actual stream names one, the two are paired, and every later packet must name them
 * together again.
 */
final class EntityPairing {
	private final Map<Integer, Integer> expectedToActual = new HashMap<>();
	private final Map<Integer, Integer> actualToExpected = new HashMap<>();

	/**
	 * Pairs two entity IDs, or tells how they break an earlier pairing.
	 *
	 * @return {@code null} when they are paired, else why they cannot be
	 */
	@Nullable String pair(int expected, int actual) {
		Integer paired = expectedToActual.get(expected);
		if (paired != null && paired != actual)
			return "expected entity " + expected + " is entity " + paired + " of the actual stream, which names "
					+ actual + " here";
		Integer back = actualToExpected.get(actual);
		if (back != null && back != expected)
			return "actual entity " + actual + " is entity " + back + " of the expected stream, which names "
					+ expected + " here";

		expectedToActual.put(expected, actual);
		actualToExpected.put(actual, expected);
		return null;
	}
}
