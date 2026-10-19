package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

/**
 * How many chunks per tick a client of the releases from Minecraft 1.20.2 on asks the server for, as the vanilla
 * client paces chunk batches: the server sends a batch of chunks between a start and a finish, and waits for the
 * client to acknowledge it with the rate it wants next before it sends more. The rate follows the time the client took
 * per chunk, averaged over the batches so far with each new sample clamped to a third and three times the average, so
 * that the chunks of a tick take 7 milliseconds. One pacing belongs to one connection.
 *
 * <pre>{@code
 * pacing.started();                                     // on the batch start
 * float desired = pacing.finished(finish.getBatchSize()); // on the batch finish, sent back to the server
 * }</pre>
 */
public final class ChunkBatchPacing {
	private static final double INITIAL_NANOS_PER_CHUNK = 2_000_000;
	private static final double NANOS_PER_TICK_FOR_CHUNKS = 7_000_000;
	private static final double CLAMP_FACTOR = 3;
	private static final int MAX_OLD_SAMPLES_WEIGHT = 49;

	private double nanosPerChunk = INITIAL_NANOS_PER_CHUNK;
	private int oldSamplesWeight = 1;
	private long batchStart = System.nanoTime();

	/**
	 * Records that a batch started.
	 */
	public synchronized void started() {
		batchStart = System.nanoTime();
	}

	/**
	 * Records that a batch finished and returns the rate to acknowledge it with.
	 *
	 * @param batchSize the number of chunks the batch carried
	 * @return the chunks per tick the client wants next
	 */
	public synchronized float finished(int batchSize) {
		if (batchSize > 0) sample((double) (System.nanoTime() - batchStart) / batchSize);

		return (float) (NANOS_PER_TICK_FOR_CHUNKS / nanosPerChunk);
	}

	/**
	 * Adds the time one chunk of a batch took to the average, clamped around the average so far.
	 */
	private void sample(double nanos) {
		double clamped = Math.clamp(nanos, nanosPerChunk / CLAMP_FACTOR, nanosPerChunk * CLAMP_FACTOR);
		nanosPerChunk = (nanosPerChunk * oldSamplesWeight + clamped) / (oldSamplesWeight + 1);
		oldSamplesWeight = Math.min(MAX_OLD_SAMPLES_WEIGHT, oldSamplesWeight + 1);
	}
}
