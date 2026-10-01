package me.whereareiam.anvil.testkit.tests.server.session;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.runner.RunnerSession;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ToolingCapabilitiesSystemTest {
	@ParameterizedTest
	@ValueSource(strings = {"paper-1.21.11", "paper-26.1.2"})
	void discoversBuiltinControlsAndObservesNativeConnectionChanges(String name) {
		var scenario = CompatibilityScenarioFactory.scenarios().stream()
				.filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
		AtomicReference<Session> connection = new AtomicReference<>();
		AtomicReference<Messages> messages = new AtomicReference<>();
		scenario = scenario.toBuilder().setupHook(context -> {
			var player = context.players().create("ToolingProbe");
			Session session = player.capability(Session.class);
			Messages channel = player.capability(Messages.class);
			session.connect();
			session.connected();
			channel.received("anvil:welcome");
			connection.set(session);
			messages.set(channel);
		}).build();
		var options = EngineOptions.builder().eulaAccepted(true).workDirectory(Path.of("build", "tooling-actions-live")).build();
		String identity = "embedded:" + scenario.getName();
		try (RunnerSession runner = new RunnerSession(() -> AnvilLauncher.create(options),
				me.whereareiam.anvil.runner.scenario.ScenarioRepository.fromScenarios(List.of(scenario)))) {
			runner.start(identity, null);
			assertEquals("Connected", runner.snapshot().getObservations().stream()
					.filter(value -> value.getDefinition().getId().equals("anvil.session.connection")).findFirst().orElseThrow().getValue().getText());
			assertTrue(runner.invoke(request(runner, "anvil.messages.command", "anvil-fixture ping")).isSuccessful());
			messages.get().received("anvil:pong", Duration.ofSeconds(10));
			assertTrue(runner.invoke(request(runner, "anvil.messages.chat", "tooling-extension-chat")).isSuccessful());
			messages.get().received("tooling-extension-chat", Duration.ofSeconds(10));
			connection.get().disconnect();
			connection.get().disconnected();
			assertEquals("Disconnected", runner.snapshot().getObservations().stream()
					.filter(value -> value.getDefinition().getId().equals("anvil.session.connection")).findFirst().orElseThrow().getValue().getText());
			assertTrue(runner.snapshot().getActions().stream().noneMatch(action -> action.getAvailability().isEnabled()));
			assertThrows(IllegalStateException.class, () -> runner.invoke(request(runner, "anvil.messages.command", "anvil-fixture ping")));
		}
	}

	private ActionRequest request(RunnerSession runner, String id, String text) {
		return ActionRequest.builder().sessionId(runner.snapshot().getSessionId()).actionId(id)
				.target(ActionTarget.builder().type(ActionTargetType.PLAYER).name("ToolingProbe").build())
				.argument("text", text).build();
	}
}
