package me.whereareiam.anvil.testkit.physics.harness;

import java.util.ArrayList;
import java.util.List;

/**
 * A packet the client sent, as a trace writes it: the name and fields of Anvil's protocol packet that stands for it,
 * the fields the release's own packet carries, each double and float exact by {@link Double#toHexString(double)} and
 * {@link Float#toHexString(float)}, enums by name and booleans as {@code true} or {@code false}.
 *
 * <pre>{@code
 * ServerboundMovePlayerPosRot x=0x1.2p2 y=0x1.0p6 z=0x1.5p3 yaw=0x1.68p6 pitch=0x0.0p0 onGround=false
 * }</pre>
 */
public final class PacketLine {
	private final StringBuilder text;

	private PacketLine(String name) {
		this.text = new StringBuilder(name);
	}

	/**
	 * Starts the line of a packet.
	 *
	 * @param name the name of Anvil's protocol packet, such as {@code ServerboundMovePlayerPosRot}
	 * @return the line
	 */
	public static PacketLine of(String name) {
		return new PacketLine(name);
	}

	/**
	 * Adds a field, written exactly.
	 *
	 * @param field the field's name in Anvil's packet
	 * @param value its value
	 * @return this line
	 */
	public PacketLine with(String field, double value) {
		return field(field, Double.toHexString(value));
	}

	/**
	 * Adds a field, written exactly.
	 *
	 * @param field the field's name in Anvil's packet
	 * @param value its value
	 * @return this line
	 */
	public PacketLine with(String field, float value) {
		return field(field, Float.toHexString(value));
	}

	/**
	 * Adds a field, written in decimal.
	 *
	 * @param field the field's name in Anvil's packet
	 * @param value its value
	 * @return this line
	 */
	public PacketLine with(String field, int value) {
		return field(field, Integer.toString(value));
	}

	/**
	 * Adds a field, written as true or false.
	 *
	 * @param field the field's name in Anvil's packet
	 * @param value its value
	 * @return this line
	 */
	public PacketLine with(String field, boolean value) {
		return field(field, Boolean.toString(value));
	}

	/**
	 * Adds a field, written by the name of Anvil's constant, which the game's constant shares.
	 *
	 * @param field the field's name in Anvil's packet
	 * @param value its value
	 * @return this line
	 */
	public PacketLine with(String field, Enum<?> value) {
		return field(field, value.name());
	}

	private PacketLine field(String field, String value) {
		text.append(' ').append(field).append('=').append(value);
		return this;
	}

	@Override
	public String toString() {
		return text.toString();
	}

	/**
	 * The packets one tick sent, collected as the client sends them.
	 */
	public static final class Sent {
		private final List<PacketLine> lines = new ArrayList<>();

		/**
		 * Adds a packet the client sent.
		 *
		 * @param line the packet's line
		 */
		public void add(PacketLine line) {
			lines.add(line);
		}

		/**
		 * Returns the packets sent since the last call and forgets them.
		 *
		 * @return the lines in send order
		 */
		public List<PacketLine> drain() {
			List<PacketLine> drained = new ArrayList<>(lines);
			lines.clear();
			return drained;
		}
	}
}
