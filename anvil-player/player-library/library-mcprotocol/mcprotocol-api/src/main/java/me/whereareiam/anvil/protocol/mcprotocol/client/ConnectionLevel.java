package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import org.jetbrains.annotations.NotNull;

/**
 * The level one connection's client is in, which the translations of the connection's {@link PacketTable} share: the
 * translation of the login and the respawn records the level each one names, and a translation that cannot read its
 * packets without it, such as that of a chunk column, whose bytes hold as many sections as the level has and may end
 * in padding, reads it. A segment's adapter creates one with each table and hands it to the translations that share
 * it.
 *
 * <pre>{@code
 * ConnectionLevel level = new ConnectionLevel();
 * PacketTable<Packet> packets = new PacketTable<>(List.of(new ConnectionPackets(level), new WorldPackets(level)));
 * }</pre>
 */
public final class ConnectionLevel {
	private volatile Dimension dimension;

	/**
	 * Records the level the client joined or respawned in.
	 *
	 * @param dimension the level
	 */
	public void enter(@NotNull Dimension dimension) {
		this.dimension = dimension;
	}

	/**
	 * Returns the level the client is in.
	 *
	 * @return the level the last login or respawn named
	 * @throws IllegalStateException when the connection named no level yet
	 */
	public @NotNull Dimension current() {
		Dimension current = dimension;
		if (current == null) throw new IllegalStateException("The server sent level data before it named the level");

		return current;
	}
}
