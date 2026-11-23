package me.whereareiam.anvil.runner.command;

import me.whereareiam.anvil.runner.model.command.RunnerCommand;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RunnerCommandParserTest {
	@Test
	void preservesTheCompleteSendCommand() {
		RunnerCommand command = RunnerCommandParser.parse("send server say hello from Anvil");

		assertEquals(RunnerCommandType.SEND, command.type());
		assertEquals("send", command.token());
		assertEquals("server", command.arguments().get(0));
		assertEquals("say hello from Anvil", command.arguments().get(1));
	}

	@Test
	void preservesJsonActionInputsAsOneArgument() {
		var command = RunnerCommandParser.parse("action external.inspect process proxy {\"key\": \"hello world\"}");
		assertEquals(RunnerCommandType.ACTION, command.type());
		assertEquals(List.of("external.inspect", "process", "proxy", "{\"key\": \"hello world\"}"), command.arguments());
	}

	@Test
	void mapsUnknownInputToUnknownCommandType() {
		RunnerCommand command = RunnerCommandParser.parse("teleport server");

		assertEquals(RunnerCommandType.UNKNOWN, command.type());
		assertEquals("teleport", command.token());
	}
}
