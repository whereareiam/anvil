package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.edition.java.protocol.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.edition.java.protocol.packet.cookie.ServerboundCookieResponse;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.clientbound.ClientboundCookieRequestPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Cookies of the MCProtocolLib 1.21.11 release, which moves the request and the response into their own cookie
 * package. The request serves the login, configuration and game phases, so one response answers a
 * request in any of them.
 */
public final class CookiePackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundCookieRequest.class, ClientboundStoreCookie.class, ServerboundCookieResponse.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundCookieRequestPacket request)
			return new ClientboundCookieRequest(request.getKey().asString());
		if (packet instanceof ClientboundStoreCookiePacket store)
			return new ClientboundStoreCookie(store.getKey().asString(), store.getPayload());

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundCookieResponse response)) return null;

		return new ServerboundCookieResponsePacket(Key.key(response.key()), response.payload());
	}
}
