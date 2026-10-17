package me.whereareiam.anvil.capability.interaction;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker-side interaction behavior over the packets of one library release: it registers the item, block and
 * entity operations for a player and sends each action through the player's current native session. The binding
 * owns what does not depend on the release:
 * <ul>
 *     <li>the player's block-change sequence, incremented for every item use and block use;</li>
 *     <li>the shared view rotation sent with an item use;</li>
 *     <li>the order of the arm swing: before an item use, after a block use and after an entity interaction.</li>
 * </ul>
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class InteractionBinding<S> {
	private final @NotNull InteractionPackets<S> packets;

	/**
	 * Installs the interaction operations for one player with a sequence of its own. The binding keeps no native
	 * listeners, so the returned binding releases nothing.
	 *
	 * @param player native lifecycle, session access and view rotation of the player
	 * @param operations typed operation registration for the player
	 * @return binding owned until the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		PlayerInteractions interactions = new PlayerInteractions(player);
		operations.register(InteractionOperations.ITEM, use -> {
			interactions.useItem(use);
			return null;
		});
		operations.register(InteractionOperations.BLOCK, use -> {
			interactions.useBlock(use);
			return null;
		});
		operations.register(InteractionOperations.ENTITY, use -> {
			interactions.useEntity(use);
			return null;
		});

		return () -> { };
	}

	/**
	 * One player's interactions. Each action resolves the native session before taking a sequence number or sending
	 * a packet, so an action of a disconnected player has no effect.
	 */
	@RequiredArgsConstructor
	private final class PlayerInteractions {
		private final PlayerBindingContext<Object> player;
		private final AtomicInteger sequence = new AtomicInteger();

		private void useItem(ItemUse use) {
			S session = session();
			ViewRotation view = player.viewRotation();
			packets.swing(session, use.getHand());
			packets.useItem(session, use.getHand(), sequence.incrementAndGet(), view.getYaw(), view.getPitch());
		}

		private void useBlock(BlockUse use) {
			S session = session();
			BlockPosition position = BlockPosition.builder().x(use.getX()).y(use.getY()).z(use.getZ()).build();
			packets.useItemOn(session, position, use.getFace(), use.getHand(), sequence.incrementAndGet());
			packets.swing(session, use.getHand());
		}

		private void useEntity(EntityUse use) {
			S session = session();
			packets.entity(session, use.getEntityId(), use.getInteraction(), use.getHand());
			packets.swing(session, use.getHand());
		}

		private S session() {
			return packets.sessionType().cast(player.nativeSession());
		}
	}
}
