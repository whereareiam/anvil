package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import io.netty.buffer.ByteBuf;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import org.jetbrains.annotations.NotNull;

/**
 * The chunk columns and block updates of the MCProtocolLib 1.21.11 release, whose sections start with their count of
 * blocks other than air.
 */
public final class WorldPackets extends WorldTranslation {
	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, whose section count a column's bytes hold
	 */
	public WorldPackets(@NotNull ConnectionLevel level) {
		super(level);
	}

	@Override
	protected void skipCounts(@NotNull ByteBuf buf) {
		buf.readShort();
	}
}
