package me.whereareiam.anvil.runner.command;

import me.whereareiam.anvil.runner.model.command.RunnerArguments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RunnerArgumentsParserTest {
	@Test
	void parsesOneScenarioSelection() {
		RunnerArguments arguments = RunnerArgumentsParser.parse(new String[]{"--scenario=manual"});

		assertEquals("manual", arguments.scenario());
		assertFalse(arguments.list());
	}

	@Test
	void parsesDirectDefinitionSelection() {
		RunnerArguments arguments = RunnerArgumentsParser.parse(new String[]{"--definition=example.ManualScenario"});

		assertEquals("example.ManualScenario", arguments.definition());
		assertTrue(arguments.scenario() == null);
	}

	@Test
	void rejectsConflictingSelections() {
		assertThrows(IllegalArgumentException.class,
				() -> RunnerArgumentsParser.parse(new String[]{"--scenario=manual", "--definition=example.ManualScenario"}));
	}
}
