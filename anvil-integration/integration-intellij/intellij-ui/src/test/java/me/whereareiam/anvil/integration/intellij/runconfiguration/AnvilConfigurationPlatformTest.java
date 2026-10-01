package me.whereareiam.anvil.integration.intellij.runconfiguration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JComboBox;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.model.CatalogSnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

public class AnvilConfigurationPlatformTest extends UiPlatformTestCase {

	public void testSavedConfigurationRestoresStableScenarioIdentity() {
		var factory =
				ConfigurationTypeUtil.findConfigurationType(AnvilConfigurationType.class)
						.getConfigurationFactories()[0];
		var original = (AnvilRunConfiguration) factory.createTemplateConfiguration(getProject());
		original.setSourceId("fixture:integration");
		original.setDefinition("example.RegistrationScenarios");
		original.setScenario("returning-player");
		Element state = new Element("configuration");
		original.writeExternal(state);

		var restored = (AnvilRunConfiguration) factory.createTemplateConfiguration(getProject());
		restored.readExternal(state);
		assertEquals(original.getSourceId(), restored.getSourceId());
		assertEquals(original.getDefinition(), restored.getDefinition());
		assertEquals(original.getScenario(), restored.getScenario());
		assertEquals("", restored.getProcessId());
		assertNull(state.getAttribute("processId"));
		assertNull(state.getAttribute("projectDirectory"));
		assertNull(state.getAttribute("exportTask"));
		assertNull(state.getAttribute("manifestPath"));
	}

	public void testSavedComponentTargetSurvivesXmlAndCanBeCleared() {
		var original = scenarioConfiguration();
		original.setProcessId("lobby");
		Element state = new Element("configuration");
		original.writeExternal(state);
		var restored = scenarioConfiguration();
		restored.readExternal(state);
		assertEquals("lobby", restored.getProcessId());

		restored.setProcessId("");
		restored.writeExternal(state);
		assertNull(state.getAttribute("processId"));
		original.readExternal(state);
		assertEquals("An omitted target must mean the complete scenario", "", original.getProcessId());
	}

	public void testWholeScenarioAndComponentsHaveDistinctStableSavedConfigurations() {
		var source = source("authentication");
		var server = process("lobby", "Lobby", ProcessRole.SERVER);
		var proxy = process("proxy", "Entry proxy", ProcessRole.PROXY);
		var scenario =
				ScenarioDescriptor.builder()
						.definition("example.RegistrationScenarios")
						.name("returning-player")
						.displayName("Returning player")
						.process(server)
						.process(proxy)
						.build();
		var manager = RunManager.getInstance(getProject());
		int before = manager.getAllSettings().size();
		var whole = configurations().save(source, scenario, null);
		var lobby = configurations().save(source, scenario, server);
		var entry = configurations().save(source, scenario, proxy);
		assertEquals(before + 3, manager.getAllSettings().size());
		assertEquals("", ((AnvilRunConfiguration) whole.getConfiguration()).getProcessId());
		assertEquals("lobby", ((AnvilRunConfiguration) lobby.getConfiguration()).getProcessId());
		assertEquals("proxy", ((AnvilRunConfiguration) entry.getConfiguration()).getProcessId());
		assertEquals("Returning player · Lobby", lobby.getName());
		assertSame(lobby, configurations().save(source, scenario, server));
		assertSame(whole, configurations().save(source, scenario, null));
		assertEquals(before + 3, manager.getAllSettings().size());
		var otherModule = configurations().save(source("other"), scenario, server);
		assertNotSame(lobby, otherModule);
		assertFalse(
				"Configurations from different sources need distinguishable names",
				lobby.getName().equals(otherModule.getName()));
	}

	public void testIdenticallyNamedStandaloneConfigurationDoesNotRepeatItsName() {
		var server = process("paper", "Paper", ProcessRole.SERVER);
		var scenario =
				ScenarioDescriptor.builder()
						.definition("example.Servers")
						.name("paper")
						.displayName("Paper")
						.process(server)
						.build();
		var settings =
				configurations().save(source("standalone"), scenario, server);
		assertEquals("Paper · Server", settings.getName());
		assertEquals("paper", ((AnvilRunConfiguration) settings.getConfiguration()).getProcessId());
	}

	public void testSoleDetectedModuleIsSelectedAndSavedWithoutBuildSettings() throws Exception {
		ScenarioSource source = source("authentication");
		installSources(source);
		var configuration = scenarioConfiguration();
		configuration.setProcessId("lobby");
		configuration.checkConfiguration();
		var editor = editor(configuration);
		editor.applyEditorTo(configuration);

		assertEquals(source.getId(), configuration.getSourceId());
		assertEquals("example.RegistrationScenarios", configuration.getDefinition());
		assertEquals("returning-player", configuration.getScenario());
		assertEquals("lobby", configuration.getProcessId());
	}

	public void testMultipleModulesRequireAnExplicitSelection() throws Exception {
		ScenarioSource authentication = source("authentication");
		ScenarioSource proxy = source("proxy");
		installSources(authentication, proxy);
		var configuration = scenarioConfiguration();
		assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
		var editor = editor(configuration);
		assertThrows(ConfigurationException.class, () -> editor.applyEditorTo(configuration));
		JComboBox<?> picker = findPicker(editor.createEditor());
		assertNotNull(picker);
		picker.setSelectedItem(proxy);
		editor.applyEditorTo(configuration);
		configuration.checkConfiguration();

		assertEquals(proxy.getId(), configuration.getSourceId());
	}

	public void testMissingSavedModuleCannotSilentlySelectTheOnlyReplacement() throws Exception {
		ScenarioSource replacement = source("replacement");
		installSources(replacement);
		var configuration = scenarioConfiguration();
		configuration.setSourceId("fixture:removed");
		assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
		var editor = editor(configuration);
		assertThrows(ConfigurationException.class, () -> editor.applyEditorTo(configuration));
		assertEquals("fixture:removed", configuration.getSourceId());
		JComboBox<?> picker = findPicker(editor.createEditor());
		assertNotNull(picker);
		picker.setSelectedItem(replacement);
		editor.applyEditorTo(configuration);
		configuration.checkConfiguration();

		assertEquals(replacement.getId(), configuration.getSourceId());
	}

	public void testSaveReusesRenamedConfigurationAndSelectsItWithoutLaunching() {
		var source = source("authentication");
		installSources(source);
		var lifecycle = recordingLifecycle();
		var service = configurations();
		var scenario = descriptor();
		var first = service.save(source, scenario, null);
		first.setName("My custom launcher");
		service.save(source, scenario, process("lobby", "Lobby", ProcessRole.SERVER));
		var restored = service.save(source, scenario, null);

		assertSame(first, restored);
		assertEquals("My custom launcher", restored.getName());
		assertSame(restored, RunManager.getInstance(getProject()).getSelectedConfiguration());
		assertEquals(2, RunManager.getInstance(getProject()).getAllSettings().size());
		assertEquals(0, lifecycle.launches);
	}

	public void testLaunchUsesMatchingCatalogAndDistinguishesProcessFromWholeScenario() throws Exception {
		var source = source("authentication");
		installSources(source);
		var scenario = descriptor();
		loadedCatalog(source, scenario);
		var lifecycle = recordingLifecycle();
		var configuration = scenarioConfiguration();
		configuration.setSourceId(source.getId());
		configuration.setProcessId("lobby");
		var service = configurations();

		assertSame(service.launch(configuration), lifecycle.session);
		assertSame(scenario, lifecycle.scenario);
		assertEquals(source, lifecycle.source);
		assertEquals("lobby", lifecycle.process);

		configuration.setProcessId("");
		service.launch(configuration);
		assertEquals(2, lifecycle.launches);
		assertNull(lifecycle.process);
		assertSame(scenario, lifecycle.scenario);
	}

	public void testColdLaunchDoesNotUseDescriptorsFromAnotherSource() throws Exception {
		var source = source("authentication");
		var other = source("other");
		installSources(source, other);
		var foreign = descriptor();
		loadedCatalog(other, foreign);
		var lifecycle = recordingLifecycle();
		var configuration = scenarioConfiguration();
		configuration.setSourceId(source.getId());
		configuration.setName("Saved launcher");

		configurations().launch(configuration);
		assertEquals(source, lifecycle.source);
		assertNotSame(foreign, lifecycle.scenario);
		assertEquals(configuration.getDefinition(), lifecycle.scenario.getDefinition());
		assertEquals(configuration.getScenario(), lifecycle.scenario.getName());
		assertEquals("Saved launcher", lifecycle.scenario.getDisplayName());
		assertTrue(lifecycle.scenario.getProcesses().isEmpty());
	}

	public void testLaunchResolvesSourcesAgainAfterValidation() throws Exception {
		var source = source("authentication");
		var available = installSources(source);
		var lifecycle = recordingLifecycle();
		var configuration = scenarioConfiguration();
		configuration.setSourceId(source.getId());
		configuration.checkConfiguration();
		available.set(List.of(source("replacement")));

		var failure = Assertions.assertThrows(ExecutionException.class,
				() -> configurations().launch(configuration));
		assertInstanceOf(failure.getCause(), IllegalStateException.class);
		assertEquals(0, lifecycle.launches);
	}

	public void testValidationAndLaunchRejectIncompleteIdentityWithoutStarting() {
		installSources(source("authentication"));
		var lifecycle = recordingLifecycle();
		var configuration = scenarioConfiguration();
		configuration.setDefinition(" ");
		assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
		assertThrows(ExecutionException.class,
				() -> configurations().launch(configuration));
		assertEquals(0, lifecycle.launches);
	}

	public void testLaunchRetainsLifecycleFailureCause() {
		installSources(source("authentication"));
		var lifecycle = recordingLifecycle();
		lifecycle.failure = new IllegalStateException("An environment is already running");
		var configuration = scenarioConfiguration();
		var failure = Assertions.assertThrows(ExecutionException.class,
				() -> configurations().launch(configuration));
		assertSame(lifecycle.failure, failure.getCause());
		assertEquals(lifecycle.failure.getMessage(), failure.getMessage());
	}

	public void testNativeProfileDefersLaunchAndPrintsSessionOutputOnceInItsOwnConsole() throws Exception {
		installSources(source("authentication"));
		var lifecycle = recordingLifecycle();
		var configuration = scenarioConfiguration();
		var executor = DefaultRunExecutor.getRunExecutorInstance();
		var environment = ExecutionEnvironmentBuilder.create(getProject(), executor, configuration).build();
		var state = configuration.getState(executor, environment);
		assertEquals(0, lifecycle.launches);

		var result = state.execute(executor, ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration));
		assertNotNull(result);
		assertEquals(1, lifecycle.launches);
		var console = (ConsoleViewImpl) result.getExecutionConsole();
		Disposer.register(getTestRootDisposable(), console);
		assertNotSame(EnvironmentSessionFixture.presentation(lifecycle.session).getConsole(), console);
		console.getComponent();

		EnvironmentSessionFixture.append(lifecycle.session, null, "Run window output\n", false);
		console.waitAllRequests();
		String text = console.getEditor().getDocument().getText();

		assertEquals("Output is printed once, not also through the process handler",
				1, text.split("Run window output", -1).length - 1);
		EnvironmentSessionFixture.finish(lifecycle.session, 0);
	}

	private RunConfigurationService configurations() {
		return getProject().getService(RunConfigurationService.class);
	}

	private ScenarioDescriptor descriptor() {
		return ScenarioDescriptor.builder()
				.definition("example.RegistrationScenarios")
				.name("returning-player")
				.displayName("Returning player")
				.process(process("lobby", "Lobby", ProcessRole.SERVER))
				.build();
	}

	private RecordingLifecycle recordingLifecycle() {
		var lifecycle = new RecordingLifecycle();
		ServiceContainerUtil.replaceService(getProject(), EnvironmentLifecycle.class, lifecycle, getTestRootDisposable());
		return lifecycle;
	}

	private void loadedCatalog(ScenarioSource source, ScenarioDescriptor scenario) {
		ServiceContainerUtil.replaceService(getProject(), ScenarioCatalog.class,
				new LoadedCatalog(source, scenario), getTestRootDisposable());
	}

	private final class RecordingLifecycle implements EnvironmentLifecycle {
		private int launches;
		private ScenarioSource source;
		private ScenarioDescriptor scenario;
		private String process;
		private RetainedEnvironmentSession session;
		private IllegalStateException failure;

		@Override
		public @NotNull EnvironmentSession start(@NotNull ScenarioSource source, @NotNull ScenarioDescriptor scenario) {
			return record(source, scenario, null);
		}

		@Override
		public @NotNull EnvironmentSession startProcess(
				@NotNull ScenarioSource source,
				@NotNull ScenarioDescriptor scenario,
				@NotNull String process
		) {
			return record(source, scenario, process);
		}

		private EnvironmentSession record(ScenarioSource source, ScenarioDescriptor scenario, @Nullable String process) {
			if (failure != null) throw failure;
			launches++;
			this.source = source;
			this.scenario = scenario;
			this.process = process;
			session = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable(), false);
			return session;
		}

		@Override public @NotNull List<? extends EnvironmentSession> getSessions() { return List.of(); }
		@Override public @Nullable EnvironmentSession getActiveSession() { return session; }
		@Override public boolean hasActiveSession() { return session != null; }
		@Override public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {}
	}

	@RequiredArgsConstructor
	private static final class LoadedCatalog implements ScenarioCatalog {
		private final ScenarioSource source;
		private final ScenarioDescriptor scenario;

		@Override public void load(@NotNull ScenarioSource source) { throw new AssertionError("Launching must not reload the catalog"); }
		@Override public @NotNull CatalogSnapshot snapshot() {
			return CatalogSnapshot.builder().source(source).scenarios(List.of(scenario)).state(CatalogState.READY).build();
		}
		@Override public @NotNull SessionLog getLog() { throw new AssertionError("Launching must use the session log"); }
		@Override public void cancel() { throw new AssertionError("Launching must not cancel discovery"); }
		@Override public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {}
	}

	private AnvilRunConfiguration scenarioConfiguration() {
		var factory =
				ConfigurationTypeUtil.findConfigurationType(AnvilConfigurationType.class)
						.getConfigurationFactories()[0];
		var configuration = (AnvilRunConfiguration) factory.createTemplateConfiguration(getProject());
		configuration.setDefinition("example.RegistrationScenarios");
		configuration.setScenario("returning-player");
		return configuration;
	}

	private static ProcessDefinition process(
			String id, String displayName, ProcessRole role) {
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

	private ScenarioSource source(String name) {
		return ScenarioSource.builder()
				.id("fixture:" + name)
				.displayName(name)
				.integrationId("fixture")
				.directory(Path.of(name).toAbsolutePath())
				.build();
	}

	private AtomicReference<List<ScenarioSource>> installSources(ScenarioSource... sources) {
		var available = new AtomicReference<>(List.of(sources));
		BuildIntegration integration =
				new BuildIntegration() {
					@Override
					public @NotNull String getId() {
						return "fixture";
					}

					@Override
					public @NotNull SourceListing discover(@NotNull Project project) {
						return SourceListing.builder()
								.sources(available.get())
								.status(SourceListingStatus.READY)
								.message("Projects detected")
								.build();
					}

					@Override
					public boolean canSync(@NotNull Project project) {
						return false;
					}

					@Override
					public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
						return CompletableFuture.failedFuture(
								new UnsupportedOperationException("This fixture does not sync projects."));
					}

					@Override
					public void subscribe(
							@NotNull Project project,
							@NotNull Consumer<ProjectChange> listener,
							@NotNull Disposable owner) {}

					@Override
					public @NotNull ScenarioPreparation prepare(
							@NotNull Project project, @NotNull ScenarioSource selected) {
						throw new AssertionError(
								"Editing or validating a run configuration must not execute project tooling");
					}
				};
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(integration), getTestRootDisposable());
		return available;
	}

	private @Nullable JComboBox<?> findPicker(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof JComboBox<?> picker) return picker;
			if (!(component instanceof Container nested)) continue;
			JComboBox<?> picker = findPicker(nested);
			if (picker != null) return picker;
		}

		return null;
	}
}
