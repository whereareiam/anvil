package me.whereareiam.anvil.protocol.mcprotocol.client.translation;

import me.whereareiam.anvil.protocol.java.api.model.world.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The level one connection's client is in, which the translations of the connection's {@link PacketTable} share: the
 * translation of the login and the respawn records the level each one names, and a translation that cannot read its
 * packets without it reads it, such as that of a chunk column, whose bytes hold as many sections as the level has. A
 * segment's adapter creates one with each table and hands it to the translations that share it.
 *
 * <pre>{@code
 * ConnectionLevel level = new ConnectionLevel();
 * PacketTable<Packet> packets = new PacketTable<>(List.of(
 *         new ConnectionPackets(level),
 *         new ChunkPackets(level, demand)
 * ));
 * }</pre>
 */
public final class ConnectionLevel {
	private volatile @Nullable Level level;

	/**
	 * Records the level the client joined or respawned in.
	 *
	 * @param level the level
	 */
	public void enter(@NotNull Level level) {
		this.level = level;
	}

	/**
	 * Returns the level the client is in.
	 *
	 * @return the level the connection's last login or respawn named, or nothing before the first
	 */
	public @NotNull Optional<Level> current() {
		return Optional.ofNullable(level);
	}
}
