package me.whereareiam.anvil.capability.interaction.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.Direction;
import me.whereareiam.anvil.protocol.java.api.type.InteractAction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Installs the built-in interaction capability for players that speak the Java Edition protocol. The extension owns
 * what does not depend on the release:
 * <ul>
 *     <li>the player's block-change sequence, numbering every item use and block use the player sent;</li>
 *     <li>the shared view rotation sent with an item use;</li>
 *     <li>the center of the face a block use targets;</li>
 *     <li>the order of the arm swing: before an item use, after a block use and after an entity interaction.</li>
 * </ul>
 * An attack is sent in a packet of its own where the release carries one, from Minecraft 26.1 on, and as the attack
 * action of the entity interaction before. An action of a disconnected player sends nothing and takes no sequence
 * number.
 */
public final class JavaInteractionExtension implements WorkerExtension<Object> {
	private static final float FACE_CENTER = 0.5F;

	@Override
	public @NotNull String id() {
		return InteractionChannel.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.empty();
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		PlayerInteractions interactions = new PlayerInteractions(player, protocol, protocol.supports(ServerboundAttack.class));
		operations.register(InteractionChannel.ITEM, use -> {
			interactions.useItem(use);
			return null;
		});
		operations.register(InteractionChannel.BLOCK, use -> {
			interactions.useBlock(use);
			return null;
		});
		operations.register(InteractionChannel.ENTITY, use -> {
			interactions.useEntity(use);
			return null;
		});

		return () -> { };
	}

	private static InteractionHand hand(Hand hand) {
		return switch (hand) {
			case MAIN -> InteractionHand.MAIN_HAND;
			case OFF -> InteractionHand.OFF_HAND;
		};
	}

	private static Direction direction(BlockFace face) {
		return switch (face) {
			case DOWN -> Direction.DOWN;
			case UP -> Direction.UP;
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
			case EAST -> Direction.EAST;
		};
	}

	private static InteractAction action(EntityInteraction interaction) {
		return switch (interaction) {
			case INTERACT -> InteractAction.INTERACT;
			case ATTACK -> InteractAction.ATTACK;
		};
	}

	/**
	 * One player's interactions with the sequence of its own.
	 */
	@RequiredArgsConstructor
	private static final class PlayerInteractions {
		private final PlayerBindingContext<Object> player;
		private final JavaProtocol protocol;
		private final boolean attackPacket;
		private int sequence;

		private void useItem(ItemUse use) {
			InteractionHand hand = hand(use.getHand());
			ViewRotation view = player.viewRotation();
			protocol.send(new ServerboundSwing(hand));
			sendNumbered(next -> ServerboundUseItem.builder()
					.hand(hand)
					.sequence(next)
					.yaw(view.getYaw())
					.pitch(view.getPitch())
					.build());
		}

		private void useBlock(BlockUse use) {
			InteractionHand hand = hand(use.getHand());
			sendNumbered(next -> ServerboundUseItemOn.builder()
					.hand(hand)
					.position(BlockPosition.builder().x(use.getX()).y(use.getY()).z(use.getZ()).build())
					.face(direction(use.getFace()))
					.cursorX(FACE_CENTER)
					.cursorY(FACE_CENTER)
					.cursorZ(FACE_CENTER)
					.sequence(next)
					.build());
			protocol.send(new ServerboundSwing(hand));
		}

		private void useEntity(EntityUse use) {
			InteractionHand hand = hand(use.getHand());
			protocol.send(entity(use, hand));
			protocol.send(new ServerboundSwing(hand));
		}

		private ServerboundPacket entity(EntityUse use, InteractionHand hand) {
			if (use.getInteraction() == EntityInteraction.ATTACK && attackPacket) return new ServerboundAttack(use.getEntityId());

			return ServerboundInteract.builder()
					.entityId(use.getEntityId())
					.action(action(use.getInteraction()))
					.hand(hand)
					.build();
		}

		/**
		 * Sends a packet numbered with the next sequence number, which the player takes only once the packet was sent.
		 */
		private synchronized void sendNumbered(IntFunction<ServerboundPacket> packet) {
			int next = sequence + 1;
			protocol.send(packet.apply(next));
			sequence = next;
		}
	}
}
