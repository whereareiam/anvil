package me.whereareiam.anvil.capability.movement.java;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import me.whereareiam.anvil.protocol.worker.TestWorkerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaMovementExtensionTest {
	private static final Position POSITION = Position.builder()
			.x(1.5).y(64).z(-2.5)
			.yaw(90).pitch(-15)
			.onGround(true)
			.build();

	private final JavaMovementExtension extension = new JavaMovementExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();
	private WorkerBinding binding;

	@AfterEach
	void close() {
		if (binding != null) binding.close();
	}

	@Test
	void isDiscoveredAsTheJavaMovementExtensionForEveryLibrary() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaMovementExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.movement", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void registersEveryOperationOfTheMovementChannel() {
		binding = extension.bind(player, operations);

		assertEquals(List.of("movement.move", "movement.look", "movement.physics", "movement.input", "movement.state"),
				operations.registered());
	}

	@Test
	void sendsTheMoveAsOnePacketAndSharesTheViewRotation() {
		binding = extension.bind(player, operations);
		player.connect();

		assertNull(operations.call(MovementChannel.MOVE, POSITION));

		assertEquals(List.of(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(1.5, 64, -2.5))
				.yaw(90)
				.pitch(-15)
				.onGround(true)
				.horizontalCollision(false)
				.build()), player.sent());
		assertEquals(new ViewRotation(90, -15), player.viewRotation());
		assertEquals(POSITION, operations.call(MovementChannel.STATE, null).getPosition());
	}

	@Test
	void leavesTheViewUnchangedWhileThePlayerIsDisconnected() {
		binding = extension.bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.call(MovementChannel.MOVE, POSITION));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), player.sent());
		assertEquals(new ViewRotation(0, 0), player.viewRotation());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		assertEquals(List.of(), operations.registered());
	}
}
