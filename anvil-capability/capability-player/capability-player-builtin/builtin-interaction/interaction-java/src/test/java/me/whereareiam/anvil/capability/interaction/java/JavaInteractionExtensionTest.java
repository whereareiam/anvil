package me.whereareiam.anvil.capability.interaction.java;

import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundPunch;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.Direction;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractAction;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInteractionExtensionTest {
	private static final Set<Class<? extends JavaPacket>> INTERACTION_PACKETS = Set.of(
			ServerboundSwing.class,
			ServerboundUseItem.class,
			ServerboundUseItemOn.class,
			ServerboundInteract.class,
			ServerboundAttack.class
	);

	private final JavaInteractionExtension extension = new JavaInteractionExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice", INTERACTION_PACKETS::contains);
	private final TestOperations operations = new TestOperations();

	@Test
	void isDiscoveredAsTheJavaInteractionExtensionForEveryLibrary() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaInteractionExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.interaction", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void registersTheItemBlockAndEntityOperations() {
		WorkerBinding binding = extension.bind(player, operations);

		assertEquals(List.of("interaction.item", "interaction.block", "interaction.entity"), operations.registered());
		assertDoesNotThrow(binding::close);
	}

	@Test
	void swingsBeforeUsingTheItemWithTheNextSequenceAndTheSharedView() {
		player.viewRotation(new ViewRotation(90, -15));
		extension.bind(player, operations);
		player.connect();

		assertNull(operations.call(InteractionChannel.ITEM, new ItemUse(Hand.OFF)));

		assertEquals(List.of(
				new ServerboundSwing(InteractionHand.OFF_HAND),
				ServerboundUseItem.builder().hand(InteractionHand.OFF_HAND).sequence(1).yaw(90).pitch(-15).build()
		), player.sent());
		assertEquals(new ViewRotation(90, -15), player.viewRotation());
	}

	@Test
	void usesTheItemOnTheCenterOfTheBlockFaceBeforeSwinging() {
		extension.bind(player, operations);
		player.connect();

		assertNull(operations.call(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.WEST, Hand.MAIN)));

		assertEquals(List.of(
				ServerboundUseItemOn.builder()
						.hand(InteractionHand.MAIN_HAND)
						.position(BlockPosition.builder().x(4).y(-60).z(-7).build())
						.face(Direction.WEST)
						.cursorX(0.5F)
						.cursorY(0.5F)
						.cursorZ(0.5F)
						.insideBlock(false)
						.worldBorderHit(false)
						.sequence(1)
						.build(),
				new ServerboundSwing(InteractionHand.MAIN_HAND)
		), player.sent());
	}

	@Test
	void sendsEachBlockFaceAsItsDirection() {
		extension.bind(player, operations);
		player.connect();

		for (BlockFace face : BlockFace.values())
			operations.call(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, face, Hand.MAIN));

		assertEquals(List.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST),
				player.sent(ServerboundUseItemOn.class).stream().map(ServerboundUseItemOn::getFace).toList());
	}

	@Test
	void attacksAndInteractsWithEntitiesBeforeSwingingWithoutTakingASequence() {
		extension.bind(player, operations);
		player.connect();

		assertNull(operations.call(InteractionChannel.ENTITY, new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN)));
		operations.call(InteractionChannel.ENTITY, new EntityUse(43, EntityInteraction.INTERACT, Hand.OFF));
		operations.call(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(
				new ServerboundAttack(new EntityId(42)),
				new ServerboundSwing(InteractionHand.MAIN_HAND),
				ServerboundInteract.builder()
						.entityId(new EntityId(43))
						.action(InteractAction.INTERACT)
						.hand(InteractionHand.OFF_HAND)
						.build(),
				new ServerboundSwing(InteractionHand.OFF_HAND),
				new ServerboundSwing(InteractionHand.MAIN_HAND),
				ServerboundUseItem.builder().hand(InteractionHand.MAIN_HAND).sequence(1).build()
		), player.sent());
	}

	@Test
	void followsAnAttackWithAPunchAndSendsNoSwingWhereTheServerPlaysIt() {
		Set<Class<? extends JavaPacket>> carried = Set.of(
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class,
				ServerboundPunch.class
		);
		TestJavaPlayer punching = new TestJavaPlayer("Alice", carried::contains);
		extension.bind(punching, operations);
		punching.connect();

		operations.call(InteractionChannel.ENTITY, new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN));
		operations.call(InteractionChannel.ENTITY, new EntityUse(43, EntityInteraction.INTERACT, Hand.OFF));
		operations.call(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(
				new ServerboundAttack(new EntityId(42)),
				new ServerboundPunch(),
				ServerboundInteract.builder()
						.entityId(new EntityId(43))
						.action(InteractAction.INTERACT)
						.hand(InteractionHand.OFF_HAND)
						.build(),
				ServerboundUseItem.builder().hand(InteractionHand.MAIN_HAND).sequence(1).build()
		), punching.sent());
	}

	@Test
	void numbersItemAndBlockUsesInOneSequencePerPlayer() {
		TestJavaPlayer bob = new TestJavaPlayer("Bob", INTERACTION_PACKETS::contains);
		TestOperations bobOperations = new TestOperations();
		extension.bind(player, operations);
		extension.bind(bob, bobOperations);
		player.connect();
		bob.connect();

		operations.call(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));
		operations.call(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		bobOperations.call(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));
		operations.call(InteractionChannel.ITEM, new ItemUse(Hand.MAIN));

		assertEquals(List.of(1, 2, 3), sequences(player));
		assertEquals(List.of(1), sequences(bob));
	}

	@Test
	void sendsNothingAndTakesNoSequenceWhileThePlayerIsDisconnected() {
		extension.bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.call(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN)));
		assertThrows(IllegalStateException.class,
				() -> operations.call(InteractionChannel.ITEM, new ItemUse(Hand.MAIN)));
		assertThrows(IllegalStateException.class,
				() -> operations.call(InteractionChannel.ENTITY,
						new EntityUse(42, EntityInteraction.INTERACT, Hand.MAIN)));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), player.sent());

		player.connect();
		operations.call(InteractionChannel.BLOCK, new BlockUse(4, -60, -7, BlockFace.UP, Hand.MAIN));

		assertEquals(List.of(1), sequences(player));
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		assertEquals(List.of(), operations.registered());
	}

	private static List<Integer> sequences(TestJavaPlayer player) {
		List<Integer> sequences = new ArrayList<>();
		for (ServerboundPacket packet : player.sent()) {
			if (packet instanceof ServerboundUseItem use) sequences.add(use.getSequence());
			if (packet instanceof ServerboundUseItemOn use) sequences.add(use.getSequence());
		}

		return sequences;
	}
}
