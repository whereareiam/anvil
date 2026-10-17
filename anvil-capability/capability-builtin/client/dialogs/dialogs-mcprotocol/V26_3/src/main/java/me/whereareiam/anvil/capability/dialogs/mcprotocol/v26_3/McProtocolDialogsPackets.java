package me.whereareiam.anvil.capability.dialogs.mcprotocol.v26_3;

import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.capability.dialogs.packet.RegistryDialogEntry;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
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
import java.util.Map;

/**
 * Dialogs packets of MCProtocolLib releases from Minecraft 26.3, whose NBT is cloudburst's, so a received
 * compound is already an NBT tree. A show-dialog packet carries the dialog in full or, in the game, refers to it by
 * registry ID; the clear-dialog packet closes it. The client replaces its screens when it joins a level, respawns or
 * starts configuring again, and the dialog registry arrives during configuration with each entry's data as any NBT
 * tag, which for a dialog is a compound. Commands use the unsigned command packet, and a custom click carries its payload as a compound.
 */
public final class McProtocolDialogsPackets implements DialogsPackets<Session> {
	private static final Key DIALOG_REGISTRY = Key.key("minecraft:dialog");

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void command(@NotNull Session session, @NotNull String commandWithoutSlash) {
		session.send(new ServerboundChatCommandPacket(commandWithoutSlash));
	}

	@Override
	public void customClick(@NotNull Session session, @NotNull String id, @NotNull Map<String, Object> payload) {
		session.send(new ServerboundCustomClickActionPacket(Key.key(id), (NbtMap) nativeTag(payload)));
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
			case ClientboundShowDialogGamePacket show -> shown(show.getDialog(), listener);
			case ClientboundShowDialogConfigurationPacket show -> listener.shown(show.getDialog(), true);
			case ClientboundClearDialogPacket ignored -> listener.cleared();
			case ClientboundLoginPacket ignored -> listener.reset();
			case ClientboundRespawnPacket ignored -> listener.reset();
			case ClientboundStartConfigurationPacket ignored -> listener.reset();
			case ClientboundRegistryDataPacket registry when registry.getRegistry().equals(DIALOG_REGISTRY) -> listener.registry(registry(registry));
			default -> {
			}
		}
	}

	private static void shown(Holder<NbtMap> dialog, DialogsPacketListener listener) {
		if (dialog.isId()) {
			listener.shownFromRegistry(dialog.id());
			return;
		}

		listener.shown(dialog.custom(), false);
	}

	private static List<RegistryDialogEntry> registry(ClientboundRegistryDataPacket registry) {
		List<RegistryDialogEntry> entries = new ArrayList<>();
		for (RegistryEntry entry : registry.getEntries())
			entries.add(new RegistryDialogEntry(entry.getId().asString(), entry.getData() instanceof NbtMap dialog ? dialog : null));
		return entries;
	}

	/**
	 * Converts a tag of an NBT tree into cloudburst's NBT, whose lists name their element type.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object nativeTag(Object tag) {
		return switch (tag) {
			case Map<?, ?> compound -> {
				NbtMapBuilder map = NbtMap.builder();
				compound.forEach((key, value) -> map.put((String) key, nativeTag(value)));
				yield map.build();
			}
			case List<?> list -> {
				if (list.isEmpty()) yield NbtList.EMPTY;

				List<Object> elements = list.stream().map(McProtocolDialogsPackets::nativeTag).toList();
				yield new NbtList(NbtType.byClass(elements.getFirst().getClass()), elements);
			}
			default -> tag;
		};
	}
}
