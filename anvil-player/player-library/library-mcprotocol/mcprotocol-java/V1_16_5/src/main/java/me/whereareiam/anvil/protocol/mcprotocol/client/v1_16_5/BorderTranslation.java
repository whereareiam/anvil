package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The world border of the MCProtocolLib releases from 1.16.5 until 1.17.1, which move the border in milliseconds that
 * every release reports as ticks. Minecraft 1.17 split the border into a packet per change, so each release's
 * {@code BorderPackets} reads its own packets.
 */
public abstract class BorderTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundInitializeBorder.class,
				ClientboundSetBorderCenter.class,
				ClientboundSetBorderLerpSize.class,
				ClientboundSetBorderSize.class,
				ClientboundSetBorderWarningDelay.class,
				ClientboundSetBorderWarningDistance.class
		);
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Converts the milliseconds a border of these releases moves for into ticks.
	 *
	 * @param millis the milliseconds the release sent
	 * @return the ticks of 50 milliseconds
	 */
	protected static long ticks(long millis) {
		return millis / 50;
	}
}
