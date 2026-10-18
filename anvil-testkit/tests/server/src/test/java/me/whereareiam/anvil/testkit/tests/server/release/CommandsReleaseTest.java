package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads the command tree the server sends and checks commands against it.
 */
@Release
class CommandsReleaseTest {
	@Test
	void checksCommandsAgainstTheServersTree(ScenarioContext anvil) {
		Commands commands = new FixtureServer(anvil).join().capability(Commands.class);

		commands.tree(tree -> tree.literal("anvil-fixture").isPresent(), Duration.ofSeconds(10));

		assertEquals(CommandCheck.NO_ISSUES, commands.check("anvil-fixture ping"));
		assertEquals(CommandCheck.PARSE_ERRORS, commands.check("anvil-unknown-command"));
	}
}
