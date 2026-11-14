package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The player's health, death and the client's commands of the MCProtocolLib releases from 1.20.5 on; each release's
 * {@code HealthPackets} names the client's commands, which MCProtocolLib renamed for Minecraft 26.1.
 */
public abstract class HealthTranslation implements PacketTranslation<Packet> {
	private final @NotNull ComponentTranslator text;

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
		return switch (packet) {
			case ClientboundSetHealthPacket health ->
					new ClientboundSetHealth(health.getHealth(), health.getFood(), health.getSaturation());
			case ClientboundPlayerCombatKillPacket kill ->
					new ClientboundPlayerCombatKill(kill.getPlayerId(), text.translate(kill.getMessage()).plain());
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundClientCommand command)) return null;

		return new ServerboundClientCommandPacket(command(command.getAction()));
	}

	/**
	 * Returns the release's name of a client command.
	 *
	 * @param action the command
	 * @return the release's command
	 */
	protected abstract @NotNull ClientCommand command(@NotNull ClientCommandAction action);
}
