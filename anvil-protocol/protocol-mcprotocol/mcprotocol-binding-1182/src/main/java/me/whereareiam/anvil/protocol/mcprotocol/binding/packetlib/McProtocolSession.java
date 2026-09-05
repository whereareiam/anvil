package me.whereareiam.anvil.protocol.mcprotocol.binding.packetlib;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerContext;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns one native connection; shared player/capability state stays in the worker context.
 */
@RequiredArgsConstructor
final class McProtocolSession implements ProtocolWorkerSession {
	private static final String CLIENT_LOCALE = "en_us";
	private static final int VIEW_DISTANCE = 8;
	private final @NotNull ProtocolWorkerContext context;
	private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
	private final AtomicBoolean disconnectNotified = new AtomicBoolean();
	private volatile @Nullable Session session;

	@Override
	public synchronized void connect() {
		if (session != null && session.isConnected())
			return;
		disconnectNotified.set(false);
		var player = context.player();
		MinecraftProtocol protocol = new MinecraftProtocol(new GameProfile(player.uuid(), player.name()), context.accessToken());
		Session created = new TcpClientSession(context.host(), context.port(), protocol);
		created.addListener(new Listener());
		session = created;
		created.connect(true);
	}

	@Override
	public synchronized void disconnect() {
		Session current = session;
		if (current != null && current.isConnected())
			current.disconnect("Disconnected by Anvil");
	}

	@Override
	public synchronized void rejoin() {
		disconnect();
		session = null;
		connect();
	}

	@Override
	public void send(@NotNull Object packet) {
		Session current = session;
		if (current == null || !current.isConnected())
			throw new IllegalStateException("Player '" + context.player().name() + "' is not connected");
		if (!(packet instanceof Packet nativePacket))
			throw new IllegalArgumentException("Unsupported native packet: " + packet.getClass().getName());
		current.send(nativePacket);
	}

	@Override
	public void close() {
		disconnect();
		session = null;
	}

	private final class Listener extends SessionAdapter {
		@Override
		public void packetReceived(@NotNull Session current, @NotNull Packet packet) {
			if (current != session)
				return;
			if (packet instanceof ClientboundLoginPacket) {
				current.send(new ServerboundClientInformationPacket(CLIENT_LOCALE, VIEW_DISTANCE,
						ChatVisibility.FULL, true, List.of(SkinPart.values()), HandPreference.RIGHT_HAND,
						false, true));
				context.connected();
			}
			if (packet instanceof ClientboundPlayerPositionPacket position) {
				context.player().view(position.getYaw(), position.getPitch());
				current.send(new ServerboundAcceptTeleportationPacket(position.getTeleportId()));
			}
			if (packet instanceof ClientboundDisconnectPacket disconnect)
				disconnected(disconnect.getReason());
			if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
				disconnected(disconnect.getReason());
			context.received(packet);
		}

		@Override
		public void disconnected(@NotNull DisconnectedEvent event) {
			if (event.getSession() != session)
				return;
			if (disconnectNotified.compareAndSet(false, true))
				context.disconnected(event.getReason());
		}

		private void disconnected(Component reason) {
			if (disconnectNotified.compareAndSet(false, true))
				context.disconnected(plain.serialize(reason));
		}
	}
}
