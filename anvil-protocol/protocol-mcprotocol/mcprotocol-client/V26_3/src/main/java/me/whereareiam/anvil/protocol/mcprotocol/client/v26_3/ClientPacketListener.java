package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Answers the packets an MCProtocolLib 26.3 client must answer on its own and reports login, teleports and
 * disconnects with their reason flattened to plain text. The login reports the profile the server granted in its
 * login success, which arrives before the game starts.
 *
 * <p>Since Minecraft 26.3 a teleport is accepted with the position and view it results in, which the server treats
 * as the player's first movement from the teleport target. The listener therefore follows the position and view the
 * client last sent or was teleported to, and resolves each teleport's relative elements against them as the vanilla
 * client does.</p>
 */
@RequiredArgsConstructor
final class ClientPacketListener extends SessionAdapter {
	private final ClientSession session;
	private final ClientLogin login;
	private final ClientListener<? super ClientSession> listener;
	private final AtomicReference<Placement> placement = new AtomicReference<>(new Placement(0, 0, 0, 0, 0));
	private volatile ClientProfile granted;

	@Override
	public void packetReceived(Session ignored, Packet packet) {
		if (packet instanceof ClientboundLoginFinishedPacket success)
			granted = ClientProfile.builder().name(success.getProfile().getName()).uniqueId(success.getProfile().getId()).build();
		if (packet instanceof ClientboundLoginPacket) {
			session.send(new ServerboundClientInformationPacket(login.getSettings().getLocale(), login.getSettings().getViewDistance(), ChatVisibility.FULL,
					true, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, false, true, ParticleStatus.ALL));
			listener.loggedIn(session, Objects.requireNonNull(granted, "The server started the game before it granted a profile"));
		}
		if (packet instanceof ClientboundPlayerPositionPacket position) {
			Placement target = placement.updateAndGet(current -> current.teleported(position));
			listener.teleported(session, target.yaw(), target.pitch());
			session.send(new ServerboundAcceptTeleportationPacket(position.getId(), target.x(), target.y(), target.z(), target.yaw(), target.pitch()));
			session.send(ServerboundPlayerLoadedPacket.INSTANCE);
		}
		// A vanilla client answers every ping; NeoForge waits for the answer to tell a vanilla client from a modded one.
		if (packet instanceof ClientboundPingPacket ping) session.send(new ServerboundPongPacket(ping.getId()));
		if (packet instanceof ClientboundDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, plainText(disconnect.getReason()));
		if (packet instanceof ClientboundLoginDisconnectPacket disconnect)
			listener.disconnected(session, DisconnectCause.SERVER, plainText(disconnect.getReason()));
	}

	@Override
	public void packetSent(Session ignored, Packet packet) {
		if (packet instanceof ServerboundMovePlayerPosRotPacket move)
			placement.set(new Placement(move.getX(), move.getY(), move.getZ(), move.getYaw(), move.getPitch()));
		if (packet instanceof ServerboundMovePlayerPosPacket move)
			placement.updateAndGet(current -> new Placement(move.getX(), move.getY(), move.getZ(), current.yaw(), current.pitch()));
		if (packet instanceof ServerboundMovePlayerRotPacket turn)
			placement.updateAndGet(current -> new Placement(current.x(), current.y(), current.z(), turn.getYaw(), turn.getPitch()));
	}

	@Override
	public void disconnected(DisconnectedEvent event) {
		listener.disconnected(session, cause(event), event.getReason() == null ? "" : plainText(event.getReason()));
	}

	/**
	 * A close caused by the library refusing an encryption request means that the server wanted online
	 * authentication from a client that signed in with no account.
	 */
	private static DisconnectCause cause(DisconnectedEvent event) {
		return event.getCause() instanceof UnexpectedEncryptionException
				? DisconnectCause.AUTHENTICATION_REQUIRED
				: DisconnectCause.CONNECTION_LOST;
	}

	private static String plainText(Component component) {
		StringBuilder text = new StringBuilder();
		append(component, text);
		return text.toString();
	}

	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (TranslationArgument argument : value.arguments()) {
				text.append(' ');
				append(argument.asComponent(), text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}

	/**
	 * Position and view of the player, absolute in the world.
	 */
	private record Placement(double x, double y, double z, float yaw, float pitch) {
		private Placement teleported(ClientboundPlayerPositionPacket teleport) {
			List<PositionElement> relative = teleport.getRelatives();
			return new Placement(
					relative.contains(PositionElement.X) ? x + teleport.getPosition().getX() : teleport.getPosition().getX(),
					relative.contains(PositionElement.Y) ? y + teleport.getPosition().getY() : teleport.getPosition().getY(),
					relative.contains(PositionElement.Z) ? z + teleport.getPosition().getZ() : teleport.getPosition().getZ(),
					relative.contains(PositionElement.Y_ROT) ? yaw + teleport.getYRot() : teleport.getYRot(),
					relative.contains(PositionElement.X_ROT) ? pitch + teleport.getXRot() : teleport.getXRot()
			);
		}
	}
}
