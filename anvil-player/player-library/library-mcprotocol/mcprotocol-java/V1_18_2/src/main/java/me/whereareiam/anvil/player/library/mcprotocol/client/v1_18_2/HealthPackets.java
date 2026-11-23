package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.ClientCommand;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import com.github.steveice10.packetlib.packet.Packet;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The player's health, death and the client's commands of the MCProtocolLib 1.18.2 and 1.19.4 releases. Their death
 * packet also names the killer, which the protocol does not model.
 */
@RequiredArgsConstructor
public final class HealthPackets implements PacketTranslation<Packet> {
	private final @NotNull ComponentTranslator text;

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSetHealth.class, ClientboundPlayerCombatKill.class, ServerboundClientCommand.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundSetHealthPacket health ->
					new ClientboundSetHealth(health.getHealth(), health.getFood(), health.getSaturation());
			case ClientboundPlayerCombatKillPacket kill ->
					new ClientboundPlayerCombatKill(new EntityId(kill.getPlayerId()),
							text.translate(kill.getMessage()).plain());
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundClientCommand command)) return null;

		return new ServerboundClientCommandPacket(switch (command.action()) {
			case PERFORM_RESPAWN -> ClientCommand.RESPAWN;
			case REQUEST_STATS -> ClientCommand.STATS;
		});
	}
}
