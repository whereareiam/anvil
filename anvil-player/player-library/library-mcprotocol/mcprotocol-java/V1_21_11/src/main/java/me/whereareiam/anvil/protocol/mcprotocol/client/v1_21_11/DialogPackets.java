package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Dialogs of the MCProtocolLib 1.21.11 release, which also serves 26.1.2, carried as the NBT of the vanilla dialog
 * codec through {@link NbtTranslator}. The game's show-dialog packet carries the dialog or, in the game, its registry
 * ID, and the configuration one always the dialog.
 */
public final class DialogPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundShowDialog.class, ClientboundClearDialog.class, ServerboundCustomClickAction.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundShowDialogGamePacket show -> shown(show.getDialog());
			case ClientboundShowDialogConfigurationPacket show ->
					new ClientboundShowDialog(null, NbtTranslator.compound(show.getDialog()), true);
			case ClientboundClearDialogPacket ignored -> new ClientboundClearDialog();
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundCustomClickAction click)) return null;

		NbtMap payload = click.getPayload() == null ? null : NbtTranslator.map(click.getPayload());
		return new ServerboundCustomClickActionPacket(Key.key(click.getId()), payload);
	}

	private static ClientboundShowDialog shown(Holder<NbtMap> dialog) {
		if (dialog.isId()) return new ClientboundShowDialog(dialog.id(), null, false);

		return new ClientboundShowDialog(null, NbtTranslator.compound(dialog.custom()), false);
	}
}
