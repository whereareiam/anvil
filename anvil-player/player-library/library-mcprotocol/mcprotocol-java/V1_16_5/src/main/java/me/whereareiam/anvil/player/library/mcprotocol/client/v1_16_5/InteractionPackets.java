package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.Animation;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerInteractEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPlaceBlockPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerUseItemPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityAnimationPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ClientboundAnimate;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib releases from 1.16.5 until 1.17.1, whose item and block uses carry neither the
 * block-change sequence nor the view. Attacks and interactions share one entity packet, so an attack is sent as its
 * attack action, which carries neither a hand nor a point. The server's animations of entities arrive as animate
 * packets, whose off-hand swing the library names after the eating that animation 3 stood for before Minecraft 1.9.
 */
public final class InteractionPackets implements PacketTranslation<Packet> {
	/**
	 * Whether the player sneaks while it attacks, which the protocol's attack does not tell.
	 */
	private static final boolean ATTACK_SNEAKING = false;

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundAnimate.class,
				ServerboundSwing.class,
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ServerEntityAnimationPacket animate)) return null;

		return new ClientboundAnimate(new EntityId(animate.getEntityId()), action(animate.getAnimation()));
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundSwing swing -> new ClientPlayerSwingArmPacket(ValueTranslator.hand(swing.getHand()));
			case ServerboundUseItem use -> new ClientPlayerUseItemPacket(ValueTranslator.hand(use.getHand()));
			case ServerboundUseItemOn use -> useItemOn(use);
			case ServerboundInteract interact -> interact(interact);
			case ServerboundAttack attack -> new ClientPlayerInteractEntityPacket(attack.getEntityId().getValue(),
					InteractAction.ATTACK, ATTACK_SNEAKING);
			default -> null;
		};
	}

	private static AnimateAction action(Animation animation) {
		return switch (animation) {
			case SWING_ARM -> AnimateAction.SWING_MAIN_HAND;
			case DAMAGE -> AnimateAction.HURT;
			case LEAVE_BED -> AnimateAction.WAKE_UP;
			case EAT_FOOD -> AnimateAction.SWING_OFF_HAND;
			case CRITICAL_HIT -> AnimateAction.CRITICAL_HIT;
			case ENCHANTMENT_CRITICAL_HIT -> AnimateAction.MAGIC_CRITICAL_HIT;
		};
	}

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ClientPlayerPlaceBlockPacket(new Position(position.getX(), position.getY(), position.getZ()),
				ValueTranslator.face(use.getFace()), ValueTranslator.hand(use.getHand()), use.getCursorX(),
				use.getCursorY(), use.getCursorZ(), use.isInsideBlock());
	}

	private static Packet interact(ServerboundInteract interact) {
		Hand hand = ValueTranslator.hand(interact.getHand());
		return switch (interact.getAction()) {
			case INTERACT -> new ClientPlayerInteractEntityPacket(interact.getEntityId().getValue(),
					InteractAction.INTERACT, hand, interact.isSneaking());
			case INTERACT_AT -> {
				Vec3d point = interact.getPoint() == null ? Vec3d.ZERO : interact.getPoint();
				yield new ClientPlayerInteractEntityPacket(interact.getEntityId().getValue(),
						InteractAction.INTERACT_AT, (float) point.getX(), (float) point.getY(), (float) point.getZ(),
						hand, interact.isSneaking());
			}
		};
	}
}
