package me.whereareiam.anvil.capability.movement.java.physics.reference;

import me.whereareiam.anvil.protocol.java.physics.living.LivingBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla client's trace of one course in one release: one {@link Tick} per tick, after that tick ran, as
 * {@code anvil-testkit/physics/traces/<minecraft>/<course>.trace} pins it, every double written exactly by
 * {@link Double#toHexString(double)}. A tick is a state line followed by zero or more packet lines, each starting with
 * {@value #PACKET_PREFIX}, that name a packet the client sent during that tick. Packet lines before the first state
 * line are what the client sent before its first tick, such as during a join.
 */
final class ReferenceTrace {
	/**
	 * How a packet line of a trace starts.
	 */
	static final String PACKET_PREFIX = "  >";

	private ReferenceTrace() {
	}

	/**
	 * Reads a trace file.
	 *
	 * @param file the trace
	 * @return its packets before the first tick and its ticks in order
	 */
	static @NotNull Trace read(@NotNull Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read the trace " + file, failure);
		}

		List<String> before = new ArrayList<>();
		List<Tick> ticks = new ArrayList<>();
		for (String line : lines) {
			if (line.isBlank() || line.startsWith("#")) continue;
			if (!line.startsWith(PACKET_PREFIX)) {
				ticks.add(new Tick(sample(line), new ArrayList<>()));
				continue;
			}
			String packet = line.substring(PACKET_PREFIX.length()).strip();
			(ticks.isEmpty() ? before : ticks.getLast().packets()).add(packet);
		}
		return new Trace(List.copyOf(before),
				ticks.stream().map(tick -> new Tick(tick.state(), List.copyOf(tick.packets()))).toList());
	}

	private static Sample sample(String line) {
		String[] columns = line.split(" ");
		if (columns.length != 9) throw new IllegalArgumentException("A trace line has nine columns: " + line);

		return new Sample(Integer.parseInt(columns[0]), Double.parseDouble(columns[1]), Double.parseDouble(columns[2]),
				Double.parseDouble(columns[3]), Double.parseDouble(columns[4]), Double.parseDouble(columns[5]),
				Double.parseDouble(columns[6]), Boolean.parseBoolean(columns[7]), Boolean.parseBoolean(columns[8]));
	}

	/**
	 * A whole trace.
	 *
	 * @param before the packets the client sent before its first tick
	 * @param ticks the ticks in order
	 */
	record Trace(@NotNull List<String> before, @NotNull List<Tick> ticks) {
		/**
		 * Returns the ticks the course runs, numbered from 0, leaving out any before the first.
		 *
		 * @return the course's ticks in order
		 */
		@NotNull List<Tick> courseTicks() {
			return ticks.stream().filter(tick -> tick.state().tick() >= 0).toList();
		}
	}

	/**
	 * One tick of a trace.
	 *
	 * @param state what the tick left the player with
	 * @param packets the packets the client sent during the tick, as the trace writes them
	 */
	record Tick(@NotNull Sample state, @NotNull List<String> packets) {
	}

	/**
	 * What one tick left the player with: the position of its feet, its delta movement and its two collision flags.
	 */
	record Sample(int tick, double x, double y, double z, double dx, double dy, double dz, boolean onGround,
			boolean horizontalCollision) {
		/**
		 * Returns what a tick left Anvil's player with.
		 *
		 * @param tick the 0-based tick
		 * @param body the player's body after the tick
		 * @return the sample
		 */
		static @NotNull Sample of(int tick, @NotNull LivingBody body) {
			return new Sample(tick, body.getPosition().x, body.getPosition().y, body.getPosition().z,
					body.getDeltaMovement().x, body.getDeltaMovement().y, body.getDeltaMovement().z, body.isOnGround(),
					body.isHorizontalCollision());
		}

		/**
		 * Tells whether two samples are the same bit for bit.
		 *
		 * @param other the other sample
		 * @return whether every column holds the same bits
		 */
		boolean same(@NotNull Sample other) {
			return tick == other.tick && bits(x) == bits(other.x) && bits(y) == bits(other.y) && bits(z) == bits(other.z)
					&& bits(dx) == bits(other.dx) && bits(dy) == bits(other.dy) && bits(dz) == bits(other.dz)
					&& onGround == other.onGround && horizontalCollision == other.horizontalCollision;
		}

		private static long bits(double value) {
			return Double.doubleToRawLongBits(value);
		}

		@Override
		public String toString() {
			return tick + " " + Double.toHexString(x) + " " + Double.toHexString(y) + " " + Double.toHexString(z) + " "
					+ Double.toHexString(dx) + " " + Double.toHexString(dy) + " " + Double.toHexString(dz) + " "
					+ onGround + " " + horizontalCollision + "  (x=" + x + " y=" + y + " z=" + z + " dx=" + dx
					+ " dy=" + dy + " dz=" + dz + ")";
		}
	}
}
