package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInputValue;
import me.whereareiam.anvil.protocol.java.api.model.dialog.RegistryDialog;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dialogs of the MCProtocolLib 1.21.11 release, which also serves 26.1.2, read from the NBT of the vanilla dialog
 * codec by {@link DialogReader}. The game's show-dialog packet carries the dialog or, in the game, its registry ID,
 * and the configuration one always the dialog. Only the registry data of the {@code minecraft:dialog} registry is
 * decoded. A custom click's payload is the compound its action declared in SNBT, an empty one when it declared none,
 * with the click's input values put in.
 */
public final class DialogPackets implements PacketTranslation<Packet> {
	private static final Key DIALOG_REGISTRY = Key.key("minecraft:dialog");

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundShowDialog.class,
				ClientboundClearDialog.class,
				ClientboundRegistryData.class,
				ServerboundCustomClickAction.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundShowDialogGamePacket show -> shown(show.getDialog());
			case ClientboundShowDialogConfigurationPacket show ->
					new ClientboundShowDialog(null, DialogReader.read(show.getDialog()), true);
			case ClientboundClearDialogPacket ignored -> new ClientboundClearDialog();
			case ClientboundRegistryDataPacket registry when registry.getRegistry().equals(DIALOG_REGISTRY) ->
					new ClientboundRegistryData(registry(registry));
			default -> null;
		};
	}

	/**
	 * Translates a custom click, putting each input value into the declared payload compound as the client does: a
	 * text as a string, a checkbox as a byte of {@code 1} or {@code 0} and a slider as a float, each replacing a
	 * declared entry with the same key.
	 *
	 * @throws IllegalArgumentException when the declared payload is not an SNBT compound
	 */
	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundCustomClickAction click)) return null;

		NbtMapBuilder payload = click.getPayload() == null
				? NbtMap.builder()
				: NbtMapBuilder.from(NbtText.compound(click.getPayload()));
		for (DialogInputValue value : click.getValues()) put(payload, value);
		return new ServerboundCustomClickActionPacket(Key.key(click.getId()), payload.build());
	}

	private static void put(NbtMapBuilder payload, DialogInputValue value) {
		if (value.getChecked() != null) {
			payload.putBoolean(value.getKey(), value.getChecked());
			return;
		}
		if (value.getNumber() != null) {
			payload.putFloat(value.getKey(), value.getNumber());
			return;
		}

		payload.putString(value.getKey(), value.getText());
	}

	private static ClientboundShowDialog shown(Holder<NbtMap> dialog) {
		if (dialog.isId()) return new ClientboundShowDialog(dialog.id(), null, false);

		return new ClientboundShowDialog(null, DialogReader.read(dialog.custom()), false);
	}

	private static List<RegistryDialog> registry(ClientboundRegistryDataPacket registry) {
		List<RegistryDialog> dialogs = new ArrayList<>();
		for (RegistryEntry entry : registry.getEntries()) {
			NbtMap data = entry.getData();
			dialogs.add(new RegistryDialog(entry.getId().asString(), data == null ? null : DialogReader.read(data)));
		}
		return dialogs;
	}
}
