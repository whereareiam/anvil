package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ControlledProject;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jetbrains.annotations.Nullable;

public class RunActionPlatformTest extends ScenarioPlatformTestCase {
	private final RunAction action = new RunAction();

	public void testWithoutASelectionRunIsUnavailableAndSaysWhatIsMissing() {
		var event = event(DataContext.EMPTY_CONTEXT);
		action.update(event);

		assertFalse(event.getPresentation().isEnabled());
		assertEquals("Select a scenario to run", event.getPresentation().getDescription());
	}

	public void testRunStartsANewEnvironmentForTheSelectedScenario() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "main");
		var event = event(context(project.source("main"), scenario(), null, null));
		action.update(event);
		assertTrue(event.getPresentation().isEnabled());
		assertEquals("Run Scenario", event.getPresentation().getText());

		action.actionPerformed(event);

		var session = EnvironmentLifecycle.getInstance(getProject()).getActiveSession();
		assertNotNull(session);
		ControlledProject.await(() -> project.startRequests().size() == 1);
		session.stop();
	}

	public void testInsideARunningEnvironmentRunStartsTheProcessThereInsteadOfANewEnvironment() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "main");
		var lifecycle = EnvironmentLifecycle.getInstance(getProject());
		EnvironmentSession session = lifecycle.run(project.source("main"), scenario(), "lobby");
		ControlledProject.await(() -> session.getSnapshot().getState().name().equals("RUNNING"));
		var event = event(context(project.source("main"), scenario(), "proxy", session));
		action.update(event);
		assertEquals("Start Proxy", event.getPresentation().getText());
		assertTrue(event.getPresentation().isEnabled());

		action.actionPerformed(event);

		ControlledProject.await(() -> project.getRunners().getLast().getRequests().stream()
				.anyMatch(request -> request.path("operation").asText().contains("start")
						&& "proxy".equals(request.path("target").asText())));
		assertEquals(1, lifecycle.getSessions().size());
		assertEquals(1, project.getRunners().size());
		session.stop();
	}

	private AnActionEvent event(DataContext context) {
		return AnActionEvent.createEvent(action, context, null, "Anvil.Test", ActionUiKind.NONE, null);
	}

	private DataContext context(ScenarioSource source, ScenarioDescriptor scenario, @Nullable String process,
			@Nullable EnvironmentSession session) {
		return SimpleDataContext.builder()
				.add(CommonDataKeys.PROJECT, getProject())
				.add(ScenarioSource.DATA_KEY, source)
				.add(ScenarioDataKeys.SCENARIO, scenario)
				.add(ScenarioDataKeys.PROCESS, process)
				.add(ScenarioDataKeys.SESSION, session)
				.build();
	}

	private static ScenarioDescriptor scenario() {
		return ScenarioDescriptor.builder()
				.definition("example.Network")
				.name("network")
				.displayName("Network")
				.process(process("lobby", ProcessRole.SERVER))
				.process(process("proxy", ProcessRole.PROXY))
				.build();
	}

	private static ProcessDefinition process(String name, ProcessRole role) {
		return ProcessDefinition.builder()
				.name(name)
				.displayName(name)
				.role(role)
				.platform(role == ProcessRole.PROXY ? "velocity" : "paper")
				.distribution("Project distribution")
				.javaRequirement("Project default")
				.build();
	}
}
