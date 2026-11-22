package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.data.game.KnownPack;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundSelectKnownPacks;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Answers the data packs a server of the MCProtocolLib releases from 1.20.5 on offers during the configuration as a
 * vanilla client does: with those of the offered packs the client has itself. Anvil's client has the vanilla core
 * pack of the player's exact Minecraft version, {@code minecraft:core} at a version such as {@code 1.20.5}, and none
 * of the experimental feature packs a vanilla client also has. For every registry entry that pack holds, the server
 * then sends no description and the client takes the entry from its own data, which Anvil keeps for the registries it
 * reads: the vanilla dimension types and the built-in dialogs. A server that offers the core pack of another version,
 * as a version-translating proxy does, gets an empty answer and sends every entry in full. MCProtocolLib answers every
 * offer with an empty list itself unless the session turns that off, as {@link #install(Session, String)} does.
 */
public final class KnownPacksAnswer extends SessionAdapter {
	private static final String NAMESPACE = "minecraft";
	private static final String CORE = "core";

	private final KnownPack core;

	private KnownPacksAnswer(@NotNull String minecraftVersion) {
		this.core = new KnownPack(NAMESPACE, CORE, minecraftVersion);
	}

	/**
	 * Turns off MCProtocolLib's own empty answer on a session and answers its known-pack offers instead.
	 *
	 * @param session a session of the release
	 * @param minecraftVersion the Minecraft version of the player's client, such as {@code 1.20.5}
	 */
	public static void install(@NotNull Session session, @NotNull String minecraftVersion) {
		session.setFlag(MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, false);
		session.addListener(new KnownPacksAnswer(minecraftVersion));
	}

	@Override
	public void packetReceived(Session session, Packet packet) {
		if (!(packet instanceof ClientboundSelectKnownPacks offer)) return;

		List<KnownPack> known = offer.getKnownPacks().stream()
				.filter(core::equals)
				.toList();
		session.send(new ServerboundSelectKnownPacks(known));
	}
}
