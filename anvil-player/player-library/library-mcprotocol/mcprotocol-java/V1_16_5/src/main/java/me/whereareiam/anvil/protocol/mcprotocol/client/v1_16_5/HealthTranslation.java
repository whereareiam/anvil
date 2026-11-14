package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.ClientRequest;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientRequestPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerHealthPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The player's health and the client's commands of the MCProtocolLib releases from 1.16.5 until 1.17.1; each
 * release's {@code HealthPackets} reads the player's death, which is a state of the combat packet before Minecraft
 * 1.17 and a packet of its own since.
 */
public abstract class HealthTranslation implements PacketTranslation<Packet> {
	/**
	 * Translates the release's text components.
	 */
	protected final @NotNull ComponentTranslator text;

	/**
	 * Creates the translation of a release.
	 *
	 * @param text translates the release's text components
	 */
	protected HealthTranslation(@NotNull ComponentTranslator text) {
		this.text = text;
	}

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSetHealth.class, ClientboundPlayerCombatKill.class, ServerboundClientCommand.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ServerPlayerHealthPacket health)
			return new ClientboundSetHealth(health.getHealth(), health.getFood(), health.getSaturation());

		return decodeDeath(packet);
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundClientCommand command)) return null;

		return new ClientRequestPacket(switch (command.getAction()) {
			case PERFORM_RESPAWN -> ClientRequest.RESPAWN;
			case REQUEST_STATS -> ClientRequest.STATS;
		});
	}

	/**
	 * Translates the release's packet that reports the player's death.
	 *
	 * @param packet the release's packet
	 * @return the death, or {@code null} when the packet reports none
	 */
	protected abstract @Nullable ClientboundPlayerCombatKill decodeDeath(@NotNull Packet packet);
}
