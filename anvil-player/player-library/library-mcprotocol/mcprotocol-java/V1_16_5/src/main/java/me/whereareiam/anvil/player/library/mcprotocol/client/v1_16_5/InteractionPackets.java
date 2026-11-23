package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerInteractEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPlaceBlockPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerUseItemPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib releases from 1.16.5 until 1.17.1, whose item and block uses carry neither the
 * block-change sequence nor the view. Attacks and interactions share one entity packet, so an attack is sent as its
 * attack action, which carries neither a hand nor a point.
 */
public final class InteractionPackets implements PacketTranslation<Packet> {
	/**
	 * Whether the player sneaks while it attacks, which the protocol's attack does not tell.
	 */
	private static final boolean ATTACK_SNEAKING = false;

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ServerboundSwing.class,
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundSwing swing -> new ClientPlayerSwingArmPacket(ValueTranslator.hand(swing.hand()));
			case ServerboundUseItem use -> new ClientPlayerUseItemPacket(ValueTranslator.hand(use.hand()));
			case ServerboundUseItemOn use -> useItemOn(use);
			case ServerboundInteract interact -> interact(interact);
			case ServerboundAttack attack -> new ClientPlayerInteractEntityPacket(attack.entityId().value(),
					InteractAction.ATTACK, ATTACK_SNEAKING);
			default -> null;
		};
	}

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.position();
		return new ClientPlayerPlaceBlockPacket(new Position(position.x(), position.y(), position.z()),
				ValueTranslator.face(use.face()), ValueTranslator.hand(use.hand()), use.cursorX(),
				use.cursorY(), use.cursorZ(), use.insideBlock());
	}

	private static Packet interact(ServerboundInteract interact) {
		Hand hand = ValueTranslator.hand(interact.hand());
		return switch (interact.action()) {
			case INTERACT -> new ClientPlayerInteractEntityPacket(interact.entityId().value(),
					InteractAction.INTERACT, hand, interact.sneaking());
			case INTERACT_AT -> {
				Vec3d point = interact.point() == null ? Vec3d.ZERO : interact.point();
				yield new ClientPlayerInteractEntityPacket(interact.entityId().value(),
						InteractAction.INTERACT_AT, (float) point.x(), (float) point.y(), (float) point.z(),
						hand, interact.sneaking());
			}
		};
	}
}
