package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import io.netty.buffer.ByteBuf;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.WorldTranslation;
import org.jetbrains.annotations.NotNull;

/**
 * The chunk columns and block updates of the MCProtocolLib releases from 26.1 on, whose sections start with their
 * count of blocks other than air and their count of fluids.
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
		buf.readShort();
	}
}
