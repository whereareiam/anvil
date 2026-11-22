package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
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
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAnimatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib releases from 1.20.5 until 26.1, whose item and block uses carry the block-change
 * sequence. Attacks and interactions share one entity packet, so an attack is sent as its attack action, which
 * carries neither a hand nor a point. Each release's {@code InteractionPackets} builds the item use, which gained the
 * view in Minecraft 1.21, and the block use, which gained the world border flag in Minecraft 1.21.2. The server's
 * animations of entities arrive as animate packets.
 */
public abstract class InteractionTranslation implements PacketTranslation<Packet> {
	/**
	 * The hand the library writes nowhere for an attack, which the vanilla protocol sends without one.
	 */
	private static final Hand ATTACK_HAND = Hand.MAIN_HAND;
	/**
	 * Whether the player sneaks while it attacks, which the protocol's attack does not tell.
	 */
	private static final boolean ATTACK_SNEAKING = false;

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
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
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundAnimatePacket animate)) return null;

		return new ClientboundAnimate(new EntityId(animate.getEntityId()), action(animate.getAnimation()));
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundSwing swing -> new ServerboundSwingPacket(ValueTranslator.hand(swing.getHand()));
			case ServerboundUseItem use -> useItem(use);
			case ServerboundUseItemOn use -> useItemOn(use);
			case ServerboundInteract interact -> interact(interact);
			case ServerboundAttack attack -> new ServerboundInteractPacket(attack.getEntityId().getValue(),
					InteractAction.ATTACK, ATTACK_HAND, ATTACK_SNEAKING);
			default -> null;
		};
	}

	/**
	 * Builds the release's item use.
	 *
	 * @param use the item use to send
	 * @return the release's packet
	 */
	protected abstract @NotNull Packet useItem(@NotNull ServerboundUseItem use);

	/**
	 * Builds the release's block use.
	 *
	 * @param use the block use to send
	 * @return the release's packet
	 */
	protected abstract @NotNull Packet useItemOn(@NotNull ServerboundUseItemOn use);

	private static AnimateAction action(Animation animation) {
		return switch (animation) {
			case SWING_ARM -> AnimateAction.SWING_MAIN_HAND;
			case LEAVE_BED -> AnimateAction.WAKE_UP;
			case SWING_OFFHAND -> AnimateAction.SWING_OFF_HAND;
			case CRITICAL_HIT -> AnimateAction.CRITICAL_HIT;
			case ENCHANTMENT_CRITICAL_HIT -> AnimateAction.MAGIC_CRITICAL_HIT;
		};
	}

	private static Packet interact(ServerboundInteract interact) {
		Hand hand = ValueTranslator.hand(interact.getHand());
		return switch (interact.getAction()) {
			case INTERACT -> new ServerboundInteractPacket(interact.getEntityId().getValue(),
					InteractAction.INTERACT, hand, interact.isSneaking());
			case INTERACT_AT -> {
				Vec3d point = interact.getPoint() == null ? Vec3d.ZERO : interact.getPoint();
				yield new ServerboundInteractPacket(interact.getEntityId().getValue(), InteractAction.INTERACT_AT,
						(float) point.getX(), (float) point.getY(), (float) point.getZ(), hand,
						interact.isSneaking());
			}
		};
	}
}
