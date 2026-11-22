package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ConnectionTranslation;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.SynchronizedRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * The connection moments of the MCProtocolLib 1.21.11 release, whose dimension types name no world clock yet and tell
 * fast lava by their {@code minecraft:gameplay/fast_lava} environment attribute, which replaced the {@code ultrawarm}
 * flag.
 */
public final class ConnectionPackets extends ConnectionTranslation {
	/**
	 * Creates the connection translation of one connection.
	 *
	 * @param registries the connection's registries
	 * @param level the connection's level
	 */
	public ConnectionPackets(@NotNull SynchronizedRegistries registries, @NotNull ConnectionLevel level) {
		super(registries, level);
	}

	@Override
	protected boolean fastLava(@NotNull String key, @NotNull NbtCompound description) {
		return fastLavaAttribute(key, description);
	}
}
