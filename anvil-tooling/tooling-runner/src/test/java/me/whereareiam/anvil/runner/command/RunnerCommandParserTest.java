package me.whereareiam.anvil.runner.command;

import me.whereareiam.anvil.runner.model.command.RunnerCommand;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RunnerCommandParserTest {
	@Test
	void preservesTheCompleteSendCommand() {
		RunnerCommand command = RunnerCommandParser.parse("send server say hello from Anvil");

		assertEquals(RunnerCommandType.SEND, command.getType());
		assertEquals("send", command.getToken());
		assertEquals("server", command.getArguments().get(0));
		assertEquals("say hello from Anvil", command.getArguments().get(1));
	}

	@Test
	void mapsUnknownInputToUnknownCommandType() {
		RunnerCommand command = RunnerCommandParser.parse("teleport server");

		assertEquals(RunnerCommandType.UNKNOWN, command.getType());
		assertEquals("teleport", command.getToken());
	}
}
