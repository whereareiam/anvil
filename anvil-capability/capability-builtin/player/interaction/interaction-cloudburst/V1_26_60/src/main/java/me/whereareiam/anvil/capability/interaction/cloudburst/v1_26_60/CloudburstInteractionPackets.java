package me.whereareiam.anvil.capability.interaction.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction packets of CloudburstMC Protocol releases from the one that serves Bedrock 1.26.60, for every codec the
 * release has. Each use is an item use inventory transaction as a Bedrock client sends it, built from what the session
 * knows about its player: the selected hotbar slot, the item in the used hand and the position at the eyes.
 *
 * <ul>
 *     <li>An arm swing is an animate packet of the player's own entity.</li>
 *     <li>An item use is an item use transaction that clicks the air: no block position, face {@code -1} and block
 *     runtime ID {@code 0}, with a client prediction of failure, as no block interaction is predicted.</li>
 *     <li>A block use clicks the center of the targeted face and names the block runtime ID the session knows at the
 *     position, with a client prediction of success.</li>
 *     <li>An entity interaction is an item use on entity transaction, whose action is {@code 0} to interact and
 *     {@code 1} to attack, the two kinds Bedrock has; its click position is zero.</li>
 * </ul>
 *
 * <p>The off hand exists in item use transactions from protocol 2193 on, and in arm swings and entity interactions
 * from protocol 2225 on; for older protocols an action of the off hand is refused with an
 * {@link UnsupportedOperationException}, as their packets act with the main hand only. A block use is refused with an
 * {@link IllegalStateException} while the session does not know the targeted block, whose runtime ID the transaction
 * must carry. The block-change sequence and the view rotation of an item use are Java Edition values that Bedrock
 * packets do not carry, so they are ignored; the swing carries no swing source.</p>
 */
public final class CloudburstInteractionPackets implements InteractionPackets<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> {
	@SuppressWarnings("unchecked")
	private static final Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> SESSION =
			(Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>>) (Class<?>) CloudburstSession.class;
	/**
	 * Codecs from this protocol on carry the hand of an item use.
	 */
	private static final int ITEM_USE_HAND_PROTOCOL = 2193;
	/**
	 * Codecs from this protocol on carry the hand of an arm swing and of an item use on an entity.
	 */
	private static final int SWING_HAND_PROTOCOL = 2225;
	private static final int CLICK_BLOCK = 0;
	private static final int CLICK_AIR = 1;
	private static final int INTERACT = 0;
	private static final int ATTACK = 1;
	private static final int NO_FACE = -1;
	private static final int NO_BLOCK = 0;

	@Override
	public @NotNull Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> sessionType() {
		return SESSION;
	}

	/**
	 * Swings the arm of a hand with an animate packet of the player's own entity.
	 *
	 * @throws UnsupportedOperationException for the off hand before protocol 2225
	 */
	@Override
	public void swing(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, @NotNull Hand hand) {
		requireHand(session, hand, SWING_HAND_PROTOCOL, "an arm swing");
		AnimatePacket packet = new AnimatePacket();
		packet.setAction(AnimatePacket.Action.SWING_ARM);
		packet.setRuntimeEntityId(start(session).getRuntimeEntityId());
		packet.setHand(handSlot(hand));
		session.send(packet);
	}

	/**
	 * Uses the item of a hand in the air.
	 *
	 * @throws UnsupportedOperationException for the off hand before protocol 2193
	 */
	@Override
	public void useItem(
			@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session,
			@NotNull Hand hand,
			int sequence,
			float yaw,
			float pitch
	) {
		requireHand(session, hand, ITEM_USE_HAND_PROTOCOL, "an item use");
		InventoryTransactionPacket packet = itemUse(session, hand, CLICK_AIR);
		packet.setBlockPosition(Vector3i.ZERO);
		packet.setBlockFace(NO_FACE);
		packet.setClickPosition(Vector3f.ZERO);
		packet.setBlockDefinition(() -> NO_BLOCK);
		packet.setClientInteractPrediction(ItemUseTransaction.PredictedResult.FAILURE);
		session.send(packet);
	}

	/**
	 * Uses the item of a hand on the center of a block face.
	 *
	 * @throws UnsupportedOperationException for the off hand before protocol 2193
	 * @throws IllegalStateException when the session does not know the targeted block
	 */
	@Override
	public void useItemOn(
			@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session,
			@NotNull BlockPosition position,
			@NotNull BlockFace face,
			@NotNull Hand hand,
			int sequence
	) {
		requireHand(session, hand, ITEM_USE_HAND_PROTOCOL, "an item use");
		int block = session.blockAt(position.getX(), position.getY(), position.getZ())
				.orElseThrow(() -> new IllegalStateException("The Bedrock session has not received the block at " + position.getX() + " "
						+ position.getY() + " " + position.getZ() + ", whose runtime ID an item use on a block carries"));

		InventoryTransactionPacket packet = itemUse(session, hand, CLICK_BLOCK);
		packet.setBlockPosition(Vector3i.from(position.getX(), position.getY(), position.getZ()));
		packet.setBlockFace(face.ordinal());
		packet.setClickPosition(faceCenter(face));
		packet.setBlockDefinition(() -> block);
		packet.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
		session.send(packet);
	}

	/**
	 * Interacts with or attacks an entity with an item use on entity transaction.
	 *
	 * @throws UnsupportedOperationException for the off hand before protocol 2225
	 */
	@Override
	public void entity(
			@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session,
			long entityId,
			@NotNull EntityInteraction kind,
			@NotNull Hand hand
	) {
		requireHand(session, hand, SWING_HAND_PROTOCOL, "an entity interaction");
		InventoryTransactionPacket packet = new InventoryTransactionPacket();
		packet.setTransactionType(InventoryTransactionType.ITEM_USE_ON_ENTITY);
		packet.setRuntimeEntityId(entityId);
		packet.setActionType(kind == EntityInteraction.ATTACK ? ATTACK : INTERACT);
		packet.setHotbarSlot(session.selectedSlot());
		packet.setHand(handSlot(hand));
		packet.setItemInHand(held(session, hand));
		packet.setPlayerPosition(eyes(session));
		packet.setClickPosition(Vector3f.ZERO);
		session.send(packet);
	}

	/**
	 * Starts an item use transaction of the held item from the player's position.
	 */
	private static InventoryTransactionPacket itemUse(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, Hand hand, int action) {
		InventoryTransactionPacket packet = new InventoryTransactionPacket();
		packet.setTransactionType(InventoryTransactionType.ITEM_USE);
		packet.setActionType(action);
		packet.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
		packet.setHotbarSlot(session.selectedSlot());
		packet.setHand(handSlot(hand));
		packet.setItemInHand(held(session, hand));
		packet.setPlayerPosition(eyes(session));
		return packet;
	}

	private static void requireHand(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, Hand hand, int since, String action) {
		if (hand == Hand.OFF && session.protocol() < since)
			throw new UnsupportedOperationException("Bedrock protocol " + session.protocol() + " sends " + action + " with the main hand only; "
					+ "the off hand needs protocol " + since + " or newer");
	}

	/**
	 * Returns the item the used hand holds: the selected hotbar slot's or the off hand's.
	 */
	private static ItemData held(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, Hand hand) {
		return hand == Hand.OFF
				? session.item(ContainerId.OFFHAND, 0).orElse(ItemData.AIR)
				: session.item(ContainerId.INVENTORY, session.selectedSlot()).orElse(ItemData.AIR);
	}

	private static Vector3f eyes(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session) {
		ClientPosition position = session.position();
		return Vector3f.from(position.getX(), position.getY(), position.getZ());
	}

	private static GameStart start(CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session) {
		return session.start().orElseThrow(() -> new IllegalStateException("A Bedrock player interacts only after its game started"));
	}

	private static HandSlot handSlot(Hand hand) {
		return hand == Hand.OFF ? HandSlot.OFFHAND : HandSlot.MAINHAND;
	}

	/**
	 * Returns the center of a block face relative to the block's corner, which Bedrock faces number in the order of
	 * {@link BlockFace}.
	 */
	private static Vector3f faceCenter(BlockFace face) {
		return switch (face) {
			case DOWN -> Vector3f.from(0.5F, 0, 0.5F);
			case UP -> Vector3f.from(0.5F, 1, 0.5F);
			case NORTH -> Vector3f.from(0.5F, 0.5F, 0);
			case SOUTH -> Vector3f.from(0.5F, 0.5F, 1);
			case WEST -> Vector3f.from(0, 0.5F, 0.5F);
			case EAST -> Vector3f.from(1, 0.5F, 0.5F);
		};
	}
}
