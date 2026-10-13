package me.whereareiam.anvil.capability.dialogs.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogs packets of MCProtocolLib releases from Minecraft 1.21.11. A show-dialog packet carries the dialog as NBT
 * or, in the game, refers to it by registry ID; the clear-dialog packet closes it. The client replaces its screens
 * when it joins a level, respawns or starts configuring again, and the dialog registry arrives during configuration.
 * Commands use the unsigned command packet, and a custom click carries its payload as a compound.
 */
public final class McProtocolDialogsPackets implements DialogsPackets<Session> {
	private static final Key DIALOG_REGISTRY = Key.key("minecraft:dialog");

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void click(@NotNull Session session, @NotNull DialogClick click) {
		if (click.getCommand() != null) {
			session.send(new ServerboundChatCommandPacket(click.getCommand()));
			return;
		}

		session.send(new ServerboundCustomClickActionPacket(Key.key(click.getCustomId()), payload(click)));
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull DialogsPacketListener listener) {
		SessionListener dialogs = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (session.isConnected()) report(packet, listener);
			}
		};
		session.addListener(dialogs);
		return () -> session.removeListener(dialogs);
	}

	private static void report(Packet packet, DialogsPacketListener listener) {
		switch (packet) {
			case ClientboundShowDialogGamePacket show -> listener.shown(DialogReader.read(show.getDialog()));
			case ClientboundShowDialogConfigurationPacket show -> listener.shown(DialogReader.read(show.getDialog()).toBuilder()
					.configuration(true)
					.build());
			case ClientboundClearDialogPacket ignored -> listener.cleared();
			case ClientboundLoginPacket ignored -> listener.reset();
			case ClientboundRespawnPacket ignored -> listener.reset();
			case ClientboundStartConfigurationPacket ignored -> listener.reset();
			case ClientboundRegistryDataPacket registry when registry.getRegistry().equals(DIALOG_REGISTRY) -> listener.registry(registry(registry));
			default -> {
			}
		}
	}

	private static List<RegistryDialog> registry(ClientboundRegistryDataPacket registry) {
		List<RegistryDialog> dialogs = new ArrayList<>();
		for (RegistryEntry entry : registry.getEntries())
			dialogs.add(new RegistryDialog(entry.getId().asString(), entry.getData() == null ? null : DialogReader.read(entry.getData())));
		return dialogs;
	}

	private static NbtMap payload(DialogClick click) {
		NbtMapBuilder payload = click.getPayload() == null ? NbtMap.builder() : NbtMapBuilder.from(NbtText.compound(click.getPayload()));
		for (DialogValue value : click.getValues()) {
			switch (value.getType()) {
				case BOOLEAN -> payload.putBoolean(value.getKey(), Boolean.parseBoolean(value.getValue()));
				case NUMBER_RANGE -> payload.putFloat(value.getKey(), Float.parseFloat(value.getValue()));
				default -> payload.putString(value.getKey(), value.getValue());
			}
		}
		return payload.build();
	}
}
