package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkBatchPacingTest {
	@Test
	void wantsThreeAndAHalfChunksPerTickBeforeAnyBatchTookTime() {
		assertEquals(3.5f, new ChunkBatchPacing().finished(0));
	}

	@Test
	void raisesTheRateAtMostThreefoldPerBatchWhenChunksArriveFast() {
		ChunkBatchPacing pacing = new ChunkBatchPacing();
		pacing.started();

		float rate = pacing.finished(1_000_000);

		assertTrue(rate > 3.5f);
		assertTrue(rate <= 3.5f * 3 / 2 + 0.01f, "one fast sample averages with the first: " + rate);
	}
}
