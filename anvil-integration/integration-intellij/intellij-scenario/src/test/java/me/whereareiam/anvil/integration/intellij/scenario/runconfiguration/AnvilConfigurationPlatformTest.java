package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.JComboBox;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ControlledProject;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

public class AnvilConfigurationPlatformTest extends ScenarioPlatformTestCase {
	private static final String DEFINITION = "example.RegistrationScenario";
	private static final String SCENARIOS = "[{\"definition\":\"" + DEFINITION + "\",\"name\":\"returning-player\","
			+ "\"displayName\":\"Returning player\",\"entrypoint\":\"lobby\",\"processes\":[{\"name\":\"lobby\","
			+ "\"displayName\":\"Lobby\",\"role\":\"SERVER\",\"platform\":\"paper\",\"distribution\":\"Project distribution\","
			+ "\"javaRequirement\":\"Project default\",\"memoryMegabytes\":1024,\"onlineMode\":false}]}]";

	public void testIdentityRoundTripsThroughXmlAndAnOmittedProcessMeansTheWholeScenario() {
		var original = configuration();
		original.setSourceId("fixture:integration");
		original.setProcessId("lobby");
		Element state = new Element("configuration");
		original.writeExternal(state);

		var restored = configuration();
		restored.readExternal(state);
		assertEquals("fixture:integration", restored.getSourceId());
		assertEquals(DEFINITION, restored.getDefinition());
		assertEquals("lobby", restored.getProcessId());

		restored.setProcessId("");
		restored.writeExternal(state);
		assertNull(state.getAttribute("processId"));
		assertNull(state.getAttribute("scenario"));
		original.readExternal(state);
		assertEquals("", original.getProcessId());
	}

	public void testProcessesCannotBeDebuggedSoDebugIsNotOffered() {
		assertInstanceOf(configuration(), RunConfigurationWithSuppressedDefaultDebugAction.class);
	}

	public void testWholeScenarioAndEachProcessGetDistinctReusedConfigurations() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication", "other");
		var source = project.source("authentication");
		var server = process("lobby", "Lobby", ProcessRole.SERVER);
		var proxy = process("proxy", "Entry proxy", ProcessRole.PROXY);
		var scenario = ScenarioDescriptor.builder().definition(DEFINITION).name("returning-player")
				.displayName("Returning player").process(server).process(proxy).build();
		var manager = RunManager.getInstance(getProject());

		var whole = service().save(source, scenario, null);
		var lobby = service().save(source, scenario, server);
		var entry = service().save(source, scenario, proxy);
		assertEquals(3, manager.getAllSettings().size());
		assertEquals("", ((AnvilRunConfiguration) whole.getConfiguration()).getProcessId());
		assertEquals("lobby", ((AnvilRunConfiguration) lobby.getConfiguration()).getProcessId());
		assertEquals("proxy", ((AnvilRunConfiguration) entry.getConfiguration()).getProcessId());
		assertEquals("Returning player · Lobby", lobby.getName());
		assertSame(lobby, service().save(source, scenario, server));
		assertSame(whole, service().save(source, scenario, null));
		assertEquals(3, manager.getAllSettings().size());

		var otherSource = service().save(project.source("other"), scenario, server);
		assertNotSame(lobby, otherSource);
		assertFalse(lobby.getName().equals(otherSource.getName()));
	}

	public void testSavingKeepsARenamedConfigurationAndSelectsItWithoutRunning() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var source = project.source("authentication");
		var scenario = descriptor();

		var first = service().save(source, scenario, null);
		first.setName("My launcher");
		service().save(source, scenario, process("lobby", "Lobby", ProcessRole.SERVER));
		var again = service().save(source, scenario, null);

		assertSame(first, again);
		assertEquals("My launcher", again.getName());
		assertSame(again, RunManager.getInstance(getProject()).getSelectedConfiguration());
		assertTrue(project.getRunners().isEmpty());
	}

	public void testStandaloneProcessWithTheScenarioNameIsNamedByItsRole() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "standalone");
		var server = process("paper", "Paper", ProcessRole.SERVER);
		var scenario = ScenarioDescriptor.builder().definition("example.Servers").name("paper").displayName("Paper")
				.process(server).build();

		var settings = service().save(project.source("standalone"), scenario, server);

		assertEquals("Paper · Server", settings.getName());
	}

	public void testSoleSourceIsSelectedByTheEditor() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var configuration = configuration();
		configuration.setProcessId("lobby");
		configuration.checkConfiguration();

		var editor = editor(configuration);
		editor.applyEditorTo(configuration);

		assertEquals(project.source("authentication").getId(), configuration.getSourceId());
		assertEquals(DEFINITION, configuration.getDefinition());
		assertEquals("lobby", configuration.getProcessId());
	}

	public void testSeveralSourcesRequireAnExplicitChoice() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication", "proxy");
		var configuration = configuration();
		assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
		var editor = editor(configuration);
		assertThrows(ConfigurationException.class, () -> editor.applyEditorTo(configuration));

		picker(editor.createEditor()).setSelectedItem(project.source("proxy"));
		editor.applyEditorTo(configuration);
		configuration.checkConfiguration();

		assertEquals(project.source("proxy").getId(), configuration.getSourceId());
	}

	public void testMissingSavedSourceIsNeverReplacedSilently() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "replacement");
		var configuration = configuration();
		configuration.setSourceId("fixture:removed");
		assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
		var editor = editor(configuration);
		assertThrows(ConfigurationException.class, () -> editor.applyEditorTo(configuration));
		assertEquals("fixture:removed", configuration.getSourceId());

		picker(editor.createEditor()).setSelectedItem(project.source("replacement"));
		editor.applyEditorTo(configuration);
		configuration.checkConfiguration();

		assertEquals(project.source("replacement").getId(), configuration.getSourceId());
	}

	public void testLaunchUsesTheLoadedScenarioAndTargetsTheSavedProcess() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		project.setScenariosJson(SCENARIOS);
		var source = project.source("authentication");
		var catalog = ScenarioCatalog.getInstance(getProject());
		catalog.load(source);
		ControlledProject.await(() -> catalog.snapshot().getState() == ScenarioCatalog.State.READY);
		var configuration = configuration();
		configuration.setSourceId(source.getId());
		configuration.setProcessId("lobby");

		EnvironmentSession session = service().launch(configuration);

		assertSame(catalog.snapshot().getScenarios().getFirst(), session.getScenario());
		ControlledProject.await(() -> project.startRequests().size() == 1);
		var start = project.startRequests().getFirst();
		assertEquals(DEFINITION, start.path("definition").asText());
		assertEquals("lobby", start.path("target").asText());
		session.stop();
	}

	public void testColdLaunchRunsTheSavedDefinitionUnderTheConfigurationName() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var configuration = configuration();
		configuration.setSourceId(project.source("authentication").getId());
		configuration.setName("Saved launcher");

		EnvironmentSession session = service().launch(configuration);

		assertEquals(DEFINITION, session.getScenario().getDefinition());
		assertEquals("Saved launcher", session.getScenario().getDisplayName());
		ControlledProject.await(() -> project.startRequests().size() == 1);
		assertEquals(DEFINITION, project.startRequests().getFirst().path("definition").asText());
		assertTrue(project.startRequests().getFirst().path("target").isMissingNode());
		session.stop();
	}

	public void testLaunchResolvesTheSourceAgainAfterValidation() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var configuration = configuration();
		configuration.setSourceId(project.source("authentication").getId());
		configuration.checkConfiguration();
		project.setSources(List.of(ScenarioSource.builder().id("fixture:replacement").displayName("replacement")
				.integrationId("fixture").directory(project.source("authentication").getDirectory()).build()));

		var failure = Assertions.assertThrows(ExecutionException.class, () -> service().launch(configuration));

		assertInstanceOf(failure.getCause(), IllegalStateException.class);
		assertTrue(project.getRunners().isEmpty());
	}

	public void testIncompleteIdentityIsRejectedWithoutStarting() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var configuration = configuration();
		configuration.setDefinition(" ");

		assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
		assertThrows(ExecutionException.class, () -> service().launch(configuration));
		assertTrue(project.getRunners().isEmpty());
	}

	public void testAnotherRunningScenarioRefusesTheLaunchWithItsReason() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var source = project.source("authentication");
		EnvironmentSession running = EnvironmentLifecycle.getInstance(getProject()).run(source,
				ScenarioDescriptor.builder().definition("example.Other").name("other").displayName("Other").build(), null);
		var configuration = configuration();
		configuration.setSourceId(source.getId());

		var failure = Assertions.assertThrows(ExecutionException.class, () -> service().launch(configuration));

		assertInstanceOf(failure.getCause(), IllegalStateException.class);
		assertEquals("Stop “Other” to run another scenario.", failure.getMessage());
		running.stop();
	}

	public void testRunTabPrintsSessionOutputOnceAndLaunchesOnlyWhenExecuted() throws Exception {
		var project = ControlledProject.install(getTestRootDisposable(), this::tooling, "authentication");
		var configuration = configuration();
		configuration.setSourceId(project.source("authentication").getId());
		var executor = DefaultRunExecutor.getRunExecutorInstance();
		var environment = ExecutionEnvironmentBuilder.create(getProject(), executor, configuration).build();
		var state = configuration.getState(executor, environment);
		assertTrue(EnvironmentLifecycle.getInstance(getProject()).getSessions().isEmpty());

		var result = state.execute(executor, ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration));
		var session = EnvironmentLifecycle.getInstance(getProject()).getActiveSession();
		assertNotNull(session);
		var console = (ConsoleViewImpl) result.getExecutionConsole();
		Disposer.register(getTestRootDisposable(), console);
		console.getComponent();
		session.getLog().append(null, null, 0, "Run window output\n", false);
		console.waitAllRequests();
		String text = console.getEditor().getDocument().getText();

		assertEquals(1, text.split("Run window output", -1).length - 1);
		session.stop();
	}

	private RunConfigurationService service() {
		return RunConfigurationService.getInstance(getProject());
	}

	private static ScenarioDescriptor descriptor() {
		return ScenarioDescriptor.builder().definition(DEFINITION).name("returning-player").displayName("Returning player")
				.process(process("lobby", "Lobby", ProcessRole.SERVER)).build();
	}

	private AnvilRunConfiguration configuration() {
		var configuration = (AnvilRunConfiguration) AnvilConfigurationType.getInstance().factory()
				.createTemplateConfiguration(getProject());
		configuration.setDefinition(DEFINITION);
		return configuration;
	}

	private static ProcessDefinition process(String id, String displayName, ProcessRole role) {
		return ProcessDefinition.builder()
				.name(id)
				.displayName(displayName)
				.role(role)
				.platform(role == ProcessRole.SERVER ? "paper" : "velocity")
				.distribution("Project distribution")
				.javaRequirement("Project default")
				.build();
	}

	private RunConfigurationEditor editor(AnvilRunConfiguration configuration) {
		var editor = (RunConfigurationEditor) configuration.getConfigurationEditor();
		Disposer.register(getTestRootDisposable(), editor);
		editor.resetEditorFrom(configuration);
		return editor;
	}

	private static JComboBox<?> picker(Container container) {
		JComboBox<?> found = findPicker(container);
		assertNotNull(found);
		return found;
	}

	private static @Nullable JComboBox<?> findPicker(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof JComboBox<?> picker) return picker;
			if (!(component instanceof Container nested)) continue;
			JComboBox<?> picker = findPicker(nested);
			if (picker != null) return picker;
		}

		return null;
	}
}
