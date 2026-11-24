package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import io.netty.channel.ChannelFutureListener;
import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * The connection of a headless client, which stands for the network: it stays connected, so the game mode ticks it,
 * and hands every packet the client sends, in order, to {@link V1_21_11Packets} instead of a channel.
 */
final class RecordingConnection extends Connection {
	private final PacketLine.Sent sent = new PacketLine.Sent();

	RecordingConnection() {
		super(PacketFlow.CLIENTBOUND);
	}

	/**
	 * The packets the client sent.
	 */
	PacketLine.Sent sent() {
		return sent;
	}

	@Override
	public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
		V1_21_11Packets.line(packet).ifPresent(sent::add);
	}

	@Override
	public boolean isConnected() {
		return true;
	}
}
