package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Moves the player, which the server sees.
 */
@Release
class MovementReleaseTest {
	@Test
	void movesThePlayerWhereTheServerSeesIt(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Messages messages = player.capability(Messages.class);

		messages.command("anvil-fixture position");
		String[] position = server.reply(player, "anvil:position:").split(":");
		player.capability(Movement.class).move(Position.builder()
				.x(Double.parseDouble(position[0]) + 0.15D)
				.y(Double.parseDouble(position[1]))
				.z(Double.parseDouble(position[2]))
				.yaw(Float.parseFloat(position[3]))
				.pitch(Float.parseFloat(position[4]))
				.onGround(true)
				.build());

		messages.received("anvil:move", Duration.ofSeconds(10));
	}
}
