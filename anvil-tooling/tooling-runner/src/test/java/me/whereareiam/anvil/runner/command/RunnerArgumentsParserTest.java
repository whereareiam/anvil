package me.whereareiam.anvil.runner.command;

import me.whereareiam.anvil.runner.model.command.RunnerArguments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RunnerArgumentsParserTest {
	@Test
	void parsesOneScenarioSelection() {
		RunnerArguments arguments = RunnerArgumentsParser.parse(new String[]{
				"--provider=example.Provider",
				"--scenario=manual"
		});

		assertEquals("example.Provider", arguments.getProvider());
		assertEquals("manual", arguments.getScenario());
        assertNull(arguments.getGroup());
		assertFalse(arguments.isList());
	}

	@Test
	void rejectsConflictingSelections() {
		assertThrows(
				IllegalArgumentException.class,
				() -> RunnerArgumentsParser.parse(new String[]{
						"--provider=example.Provider",
						"--scenario=manual",
						"--group=development"
				})
		);
	}
}
