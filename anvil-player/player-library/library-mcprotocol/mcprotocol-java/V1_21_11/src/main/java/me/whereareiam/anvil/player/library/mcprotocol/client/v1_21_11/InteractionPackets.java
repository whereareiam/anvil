package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.InteractionTranslation;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction of the MCProtocolLib 1.21.11 release, whose item use carries the view and whose block use reports
 * whether it hit the world border.
 */
public final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull Packet useItem(@NotNull ServerboundUseItem use) {
		return new ServerboundUseItemPacket(ValueTranslator.hand(use.hand()), use.sequence(), use.yaw(),
				use.pitch());
	}

	@Override
	protected @NotNull Packet useItemOn(@NotNull ServerboundUseItemOn use) {
		BlockPosition position = use.position();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.x(), position.y(), position.z()),
				ValueTranslator.direction(use.face()), ValueTranslator.hand(use.hand()), use.cursorX(),
				use.cursorY(), use.cursorZ(), use.insideBlock(), use.worldBorderHit(), use.sequence());
	}
}
