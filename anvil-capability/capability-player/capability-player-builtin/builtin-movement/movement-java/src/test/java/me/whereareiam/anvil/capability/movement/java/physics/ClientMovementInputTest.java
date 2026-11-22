package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerLoaded;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.area;
import static me.whereareiam.anvil.capability.movement.java.physics.BoundMovement.teleport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMovementInputTest {
	private final BoundMovement movement = new BoundMovement();

	@AfterEach
	void close() {
		movement.close();
	}

	@Test
	void endsNoTickBefore1_21_2AndWalksWithTheHeldKeys() {
		TestClient client = movement.physics(new TestClient("1.20.6").load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(teleport(new Vec3d(8.5, 65.0, 2.5), 0.0F, 0.0F, Set.of()));
		movement.call(MovementChannel.INPUT, MovementInput.builder().forward(true).build());

		movement.ticks(20);

		assertTrue(movement.state().getPosition().getZ() > 5.0, "the player walked to " + movement.state().getPosition());
		assertEquals(List.of(), client.player.sent(ServerboundClientTickEnd.class));
	}

	@Test
	void holdsNoKeysAndSendsNoMovementWhileDeadUntilItRespawns() {
		TestClient client = movement.physics(area(new TestClient("1.21.11")).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		movement.enter(client, new Vec3d(8.5, 65.0, 2.5));
		movement.call(MovementChannel.INPUT, MovementInput.builder().forward(true).build());
		client.player.deliver(new ClientboundSetHealth(0.0F, 20, 5.0F));
		int sent = client.player.sent().size();

		movement.ticks(10);

		assertEquals(2.5, movement.state().getPosition().getZ());
		List<?> ticked = client.player.sent().subList(sent, client.player.sent().size());
		assertEquals(new ServerboundPlayerLoaded(), ticked.getFirst(), "a dead player's level is loaded at once");
		assertTrue(ticked.stream().skip(1).allMatch(ServerboundClientTickEnd.class::isInstance), ticked.toString());

		client.player.deliver(ClientboundRespawn.builder().dimension("minecraft:overworld").dimensionType(client.dimension)
				.build());
		assertNull(movement.state().getPosition());
		movement.enter(client, new Vec3d(8.5, 65.0, 2.5));
		movement.ticks(10);
		assertTrue(movement.state().getPosition().getZ() > 2.5, "the respawned player walks again");
	}

	@Test
	void tellsSneakingThroughTheHeldKeysOnlyWhereTheReleaseCarriesThem() {
		for (boolean carried : List.of(true, false)) {
			TestClient client = movement.physics(area(new TestClient("1.21.11", type -> carried || type != ServerboundPlayerInput.class))
					.floor(0, 15, 64, 0, 15, "minecraft:stone"));
			movement.enter(client, new Vec3d(8.5, 65.0, 8.5));
			movement.call(MovementChannel.INPUT, MovementInput.builder().sneak(true).build());

			movement.ticks(3);

			boolean command = client.player.sent(ServerboundPlayerCommand.class).stream()
					.anyMatch(sent -> sent.getAction() == PlayerCommandAction.PRESS_SHIFT_KEY);
			assertEquals(!carried, command);
			assertEquals(carried, !client.player.sent(ServerboundPlayerInput.class).isEmpty());
			movement.close();
		}
	}

	@Test
	void tellsTheServerTheFlightADoubleJumpStartsBeforeTheMovementOfThatTick() {
		TestClient client = movement.physics(area(new TestClient("1.21.11")).floor(0, 15, 64, 0, 15, "minecraft:stone"));
		client.player.deliver(ClientboundPlayerAbilities.builder().mayFly(true).flyingSpeed(0.05F).walkingSpeed(0.1F)
				.build());
		movement.enter(client, new Vec3d(8.5, 65.0, 8.5));
		movement.ticks(3);
		MovementInput jump = MovementInput.builder().jump(true).build();
		movement.call(MovementChannel.INPUT, jump);
		movement.ticks(1);
		movement.call(MovementChannel.INPUT, MovementInput.NONE);
		movement.ticks(1);
		movement.call(MovementChannel.INPUT, jump);
		int sent = client.player.sent().size();

		movement.ticks(1);

		List<?> ticked = client.player.sent().subList(sent, client.player.sent().size());
		assertEquals(new ServerboundPlayerAbilities(true), ticked.getFirst(), ticked.toString());
		assertTrue(ticked.stream().anyMatch(ServerboundMovePlayerPos.class::isInstance), ticked.toString());
		assertEquals(1, client.player.sent(ServerboundPlayerAbilities.class).size());
	}
}
