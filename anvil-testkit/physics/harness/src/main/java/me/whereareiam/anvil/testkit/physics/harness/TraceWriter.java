package me.whereareiam.anvil.testkit.physics.harness;

import lombok.Value;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a course's trace: a header comment, then for each tick, after that tick ran, its state line, with every
 * double written by {@link Double#toHexString(double)}, which is exact and which {@link Double#parseDouble(String)}
 * reads back, followed by one line per packet the client sent during the tick, in send order, each starting with
 * {@value #PACKET_PREFIX}. A join's handshake is tick {@code -1}.
 */
public final class TraceWriter {
	/**
	 * The trace's columns, as its header names them.
	 */
	public static final String COLUMNS = "tick x y z dx dy dz onGround horizontalCollision";

	/**
	 * How a packet line starts.
	 */
	public static final String PACKET_PREFIX = "  > ";

	private TraceWriter() {
	}

	/**
	 * Writes the ticks of the course {@code course} as {@code file}, with Unix line endings.
	 *
	 * @param file      the trace file
	 * @param minecraft the vanilla client's Minecraft version
	 * @param course    the course's name
	 * @param ticks     the ticks in order
	 * @throws IOException when the file cannot be written
	 */
	public static void write(Path file, String minecraft, String course, List<Tick> ticks) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			writer.write("# vanilla client " + minecraft + ", course " + course + "; columns: " + COLUMNS
				+ "; then each packet the tick sent: '" + PACKET_PREFIX + "<packet> <field>=<value>...'\n");
			for (Tick tick : ticks) {
				writer.write(line(tick.getTick(), tick.getSample()) + "\n");
				for (PacketLine packet : tick.getSent())
					writer.write(PACKET_PREFIX + packet + "\n");
			}
		}
	}

	/**
	 * Formats one tick's state line.
	 *
	 * @param tick   the tick, {@code -1} for a join's handshake
	 * @param sample what the tick left
	 * @return the line without its line ending
	 */
	public static String line(int tick, TickSample sample) {
		return tick + " " + Double.toHexString(sample.getX()) + " " + Double.toHexString(sample.getY()) + " "
			+ Double.toHexString(sample.getZ()) + " " + Double.toHexString(sample.getDx()) + " "
			+ Double.toHexString(sample.getDy()) + " " + Double.toHexString(sample.getDz()) + " "
			+ sample.isOnGround() + " " + sample.isHorizontalCollision();
	}

	/**
	 * One tick of a trace: what it left and the packets it sent.
	 */
	@Value
	public static class Tick {
		int tick;
		TickSample sample;
		List<PacketLine> sent;
	}
}
