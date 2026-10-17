package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 1.18.2 release, whose item and block uses carry neither the block-change sequence
 * nor the view. Attacks and interactions share one entity packet, so an attack is sent as its attack action, which
 * carries neither a hand nor a point.
 */
final class InteractionPackets implements PacketTranslation<Packet> {
	/**
	 * The hand the library writes nowhere for an attack, which the vanilla protocol sends without one.
	 */
	private static final Hand ATTACK_HAND = Hand.MAIN_HAND;
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
			case ServerboundSwing swing -> new ServerboundSwingPacket(ValueTranslator.hand(swing.getHand()));
			case ServerboundUseItem use -> new ServerboundUseItemPacket(ValueTranslator.hand(use.getHand()));
			case ServerboundUseItemOn use -> useItemOn(use);
			case ServerboundInteract interact -> interact(interact);
			case ServerboundAttack attack -> new ServerboundInteractPacket(attack.getEntityId(), InteractAction.ATTACK,
					ATTACK_HAND, ATTACK_SNEAKING);
			default -> null;
		};
	}

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(new Position(position.getX(), position.getY(), position.getZ()),
				ValueTranslator.direction(use.getFace()), ValueTranslator.hand(use.getHand()), use.getCursorX(),
				use.getCursorY(), use.getCursorZ(), use.isInsideBlock());
	}

	private static Packet interact(ServerboundInteract interact) {
		Hand hand = ValueTranslator.hand(interact.getHand());
		return switch (interact.getAction()) {
			case INTERACT -> new ServerboundInteractPacket(interact.getEntityId(), InteractAction.INTERACT, hand,
					interact.isSneaking());
			case INTERACT_AT -> {
				Vec3d point = interact.getPoint() == null ? Vec3d.ZERO : interact.getPoint();
				yield new ServerboundInteractPacket(interact.getEntityId(), InteractAction.INTERACT_AT,
						(float) point.getX(), (float) point.getY(), (float) point.getZ(), hand, interact.isSneaking());
			}
		};
	}
}
