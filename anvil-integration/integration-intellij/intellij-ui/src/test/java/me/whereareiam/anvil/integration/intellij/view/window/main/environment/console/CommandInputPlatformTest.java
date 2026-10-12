package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.util.ui.UIUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.model.settings.CommandScope;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.ProjectEnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.settings.ProjectCommandHistory;
import me.whereareiam.anvil.integration.intellij.settings.StoredCommandHistory;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.tooling.process.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.action.ActionInvocationDialog;
import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.*;
import me.whereareiam.anvil.tooling.api.model.action.definition.*;
import me.whereareiam.anvil.tooling.api.model.action.invocation.*;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.jetbrains.annotations.NotNull;

public class CommandInputPlatformTest extends UiPlatformTestCase {
	private final List<ControlledToolingProcess> processes = new CopyOnWriteArrayList<>();
	private final ScenarioDescriptor scenario = WindowTestSupport.scenario();
	private StoredCommandHistory history;
	private RetainedEnvironmentSession run;
	private ConsolePresentation presentation;
	private ControlledToolingProcess runner;
	private ScenarioSource source;

	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected @NotNull TempDirTestFixture createTempDirTestFixture() {
		return new TempDirTestFixtureImpl();
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				Preferences.class,
				new PersistentPreferences(),
				getTestRootDisposable());
		history = new StoredCommandHistory();
		Disposer.register(getTestRootDisposable(), history);
		ServiceContainerUtil.replaceService(
				getProject(), ProjectCommandHistory.class, history, getTestRootDisposable());
		int previous = UISettings.getInstance().getConsoleCommandHistoryLimit();
		Disposer.register(
				getTestRootDisposable(),
				() -> UISettings.getInstance().setConsoleCommandHistoryLimit(previous));
		UISettings.getInstance().setConsoleCommandHistoryLimit(100);
		Path directory = Path.of(myFixture.getTempDirFixture().getTempDirPath());
		source =
				WindowTestSupport.source().toBuilder()
						.integrationId("history-fixture")
						.directory(directory)
						.build();
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS,
				List.of(new HistoryProvider(source)),
				getTestRootDisposable());
		ServiceContainerUtil.replaceService(
				getProject(),
				BuildIntegrations.class,
				new ProjectBuildIntegrations(getProject()),
				getTestRootDisposable());
		Disposer.register(
				getTestRootDisposable(), () -> processes.forEach(process -> process.complete(0)));
		String catalog = new ObjectMapper().writeValueAsString(List.of(scenario));
		ProjectEnvironmentLifecycle session =
				tooling(
						builder -> {
							boolean preparation = builder.command().getFirst().equals("history-prepare");
							ControlledToolingProcess process = new ControlledToolingProcess(!preparation);
							processes.add(process);
							if (preparation) process.complete(0);
							else {
								process.setScenariosJson(catalog);
								process.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
							}
							return process;
						});
		Disposer.register(getTestRootDisposable(), session);
		run = session.start(source, scenario);
		presentation = new ConsolePresentation(getProject(), run.getLog(), run::stop, run);
		presentation.start();
		WindowTestSupport.await(() -> run.getSnapshot().getState() == SessionState.RUNNING);
		runner = processes.getLast();
		SessionSnapshot ready =
				SessionSnapshot.builder()
						.sessionId("fixture-session")
						.definition(scenario.getDefinition())
						.scenario(scenario.getName())
						.state(SessionState.RUNNING)
						.setupComplete(true)
						.processes(
								scenario.getProcesses().stream()
										.map(
												process ->
														ProcessSnapshot.builder()
																.name(process.getName())
																.executionId(EnvironmentSessionFixture.executionId(process.getName()))
																.displayName(process.getDisplayName())
																.state(ProcessState.READY)
																.host("127.0.0.1")
																.port(25565)
																.workDirectory("/work/" + process.getName())
																.build())
										.toList())
						.actions(List.of(action("fixture.command"), action("fixture.chat")))
						.players(List.of(PlayerDescriptor.builder().name("alice").displayName("Alice").build()))
						.build();
		runner.emit(
				new ObjectMapper().writeValueAsString(Map.of("type", "snapshot", "snapshot", ready)));
		WindowTestSupport.await(() -> "fixture-session".equals(run.getSnapshot().getSessionId()));
	}

	public void testPickingNativeHistoryNeverSendsUntilExplicitEditorEnter() throws Exception {
		history.submitted(key("paper", "console"), "list");
		CommandInput input = input();
		assertTrue(input instanceof TextFieldWithHistory);
		assertEquals(List.of("list"), input.getHistory());
		input.setSelectedIndex(0);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals("list", input.getText());
		assertEquals(0, submittedCount());
		input.getTextEditor().postActionEvent();
		WindowTestSupport.await(() -> input.getText().isEmpty());
		assertEquals(1, submittedCount());
		var submitted =
				runner.getRequests().stream()
						.filter(request -> request.path("operation").asText().equals("console"))
						.findFirst()
						.orElseThrow();
		assertEquals("paper", submitted.path("target").asText());
		assertEquals("list", submitted.path("text").asText());
	}

	public void testConsoleDraftAndSelectionSurviveSnapshotsAndTargetSwitches() {
		ConsolePanel panel = new ConsolePanel(run);
		JComboBox<?> target = WindowTestSupport.find(panel, JComboBox.class);
		target.setSelectedIndex(1);
		CommandInput input = WindowTestSupport.find(panel, CommandInput.class);
		input.setText("say Paper draft");
		input.getTextEditor().select(4, 9);
		var document = input.getTextEditor().getDocument();
		panel.update();
		assertSame(document, input.getTextEditor().getDocument());
		assertEquals("Paper", input.getTextEditor().getSelectedText());
		target.setSelectedIndex(2);
		assertEquals("", input.getText());
		input.setText("velocity draft");
		target.setSelectedIndex(1);
		assertEquals("say Paper draft", input.getText());
		assertEquals("Paper", input.getTextEditor().getSelectedText());
		assertEquals(0, submittedCount());
	}

	public void testContributedActionsKeepSeparateDraftsAndHistoriesWithoutImplicitSubmission() {
		ActionDescriptor first = action("fixture.command");
		ActionDescriptor second = action("fixture.chat");
		history.submitted(key("PLAYER:alice", "fixture.command:text"), "/status");
		history.submitted(key("PLAYER:alice", "fixture.chat:text"), "hello");
		ActionInvocationDialog dialog = new ActionInvocationDialog(run, first);
		TextFieldWithHistory input =
				WindowTestSupport.find(dialog.createCenterPanel(), TextFieldWithHistory.class);
		assertEquals(List.of("/status"), input.getHistory());
		input.setText("command draft");
		dialog.close(0);
		ActionInvocationDialog other = new ActionInvocationDialog(run, second);
		assertEquals(
				List.of("hello"),
				WindowTestSupport.find(other.createCenterPanel(), TextFieldWithHistory.class)
						.getHistory());
		assertEquals(
				"",
				WindowTestSupport.find(other.createCenterPanel(), TextFieldWithHistory.class)
						.getText());
		other.close(0);
		ActionInvocationDialog reopened = new ActionInvocationDialog(run, first);
		TextFieldWithHistory restored =
				WindowTestSupport.find(reopened.createCenterPanel(), TextFieldWithHistory.class);
		assertEquals("command draft", restored.getText());
		restored.setSelectedItem("/status");
		assertEquals(0, submittedCount());
		reopened.close(0);
	}

	public void testFailedSubmissionKeepsDraftAndDoesNotRecordHistory() throws Exception {
		CommandInput input = input();
		runner.setFailCommandFlush(true);
		input.setText("list");
		input.submit();
		WindowTestSupport.await(() -> consoleText().contains("Cannot send Anvil action"));
		assertEquals("list", input.getText());
		assertTrue(history.history(key("paper", "console")).isEmpty());
	}

	public void testAcceptedSubmissionKeepsTheOtherTargetsDraftAndClearsOnlyItsOwn()
			throws Exception {
		CommandInput input = input();
		input.setText("say submitted to Paper");
		runner.holdCommandFlush();
		try {
			input.submit();
			WindowTestSupport.await(runner::isCommandFlushWaiting);
			input.scope("proxy", "console");
			input.setText("proxy draft");
			runner.releaseCommandFlush();
			WindowTestSupport.await(() -> !history.history(key("paper", "console")).isEmpty());
			assertEquals("proxy draft", input.getText());
			input.scope("paper", "console");
			assertEquals("", input.getText());
			assertEquals(List.of("say submitted to Paper"), input.getHistory());
		} finally {
			runner.releaseCommandFlush();
		}
	}

	public void testAcceptedSubmissionPreservesEditsAndSelectionMadeWhileWriting() throws Exception {
		CommandInput input = input();
		input.setText("list");
		runner.holdCommandFlush();
		try {
			input.submit();
			WindowTestSupport.await(runner::isCommandFlushWaiting);
			input.setText("next command");
			input.getTextEditor().select(5, 12);
			runner.releaseCommandFlush();
			WindowTestSupport.await(() -> !history.history(key("paper", "console")).isEmpty());
			UIUtil.dispatchAllInvocationEvents();
			assertEquals("next command", input.getText());
			assertEquals("command", input.getTextEditor().getSelectedText());
		} finally {
			runner.releaseCommandFlush();
		}
	}

	public void testBlankOrDisabledInputDoesNotSubmitAndNativeLimitCanDisableRetention()
			throws Exception {
		CommandInput input = input();
		input.setText(" \t ");
		input.submit();
		input.setText("list");
		input.setEnabled(false);
		input.submit();
		assertEquals(0, submittedCount());
		input.setEnabled(true);
		UISettings.getInstance().setConsoleCommandHistoryLimit(0);
		input.submit();
		WindowTestSupport.await(() -> input.getText().isEmpty());
		assertEquals(1, submittedCount());
		assertTrue(history.history(key("paper", "console")).isEmpty());
	}

	public void testRepeatedEnterCannotDuplicateAnInFlightDraftButEditedCommandsCanSubmit()
			throws Exception {
		CommandInput input = input();
		input.setText("first command");
		runner.holdCommandFlush();
		try {
			input.getTextEditor().postActionEvent();
			WindowTestSupport.await(runner::isCommandFlushWaiting);
			input.getTextEditor().postActionEvent();
			input.submit();
			assertEquals(1, submittedCount());
			input.setText("second command");
			input.submit();
			runner.releaseCommandFlush();
			WindowTestSupport.await(
					() -> history.history(key("paper", "console")).size() == 2 && input.getText().isEmpty());
			assertEquals(2, submittedCount());
			assertTrue(
					history
							.history(key("paper", "console"))
							.containsAll(List.of("first command", "second command")));
		} finally {
			runner.releaseCommandFlush();
		}
	}

	public void testActionSubmissionRechecksLatestAvailabilityBeforeQueuedUiUpdate() {
		ActionDescriptor action = action("fixture.command");
		ActionInvocationDialog dialog = new ActionInvocationDialog(run, action);
		TextFieldWithHistory input =
				WindowTestSupport.find(dialog.createCenterPanel(), TextFieldWithHistory.class);
		input.setText("/status");

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.actions(
								List.of(
										action.toBuilder()
												.availability(
														ActionAvailability.builder().reason("No longer available").build())
												.build()))
						.build());
		dialog.doOKAction();
		assertEquals("/status", input.getText());
		assertEquals(0, submittedCount());
		assertTrue(history.history(key("PLAYER:alice", "fixture.command:text")).isEmpty());
		dialog.close(0);
	}

	public void testActionHistoryWaitsForResultAndSensitiveValuesNeverBecomeDrafts()
			throws Exception {
		ActionDescriptor action = action("fixture.command");
		ActionInvocationDialog dialog = new ActionInvocationDialog(run, action);
		WindowTestSupport.find(dialog.createCenterPanel(), TextFieldWithHistory.class)
				.setText("inspect");
		dialog.doOKAction();
		WindowTestSupport.await(() -> submittedCount() == 1);
		assertTrue(history.history(key("PLAYER:alice", "fixture.command:text")).isEmpty());
		var request =
				runner.getRequests().stream()
						.filter(node -> node.path("operation").asText().equals("action"))
						.findFirst()
						.orElseThrow();
		assertEquals("fixture-session", request.path("sessionId").asText());
		runner.emit(
				new ObjectMapper()
						.writeValueAsString(
								Map.of(
										"type",
										"response",
										"id",
										request.path("id").asText(),
										"success",
										true,
										"result",
										ActionResult.builder()
												.message("Done")
												.columns(List.of("Value"))
												.rows(List.of(List.of("42")))
												.build())));
		WindowTestSupport.await(
				() -> !history.history(key("PLAYER:alice", "fixture.command:text")).isEmpty());
		assertTrue(WindowTestSupport.text(dialog.createCenterPanel()).contains("Done"));
		dialog.close(0);

		ActionDescriptor sensitive =
				action.toBuilder()
						.definition(
								action.getDefinition().toBuilder()
										.id("fixture.secret")
										.clearInputs()
										.inputs(
												List.of(
														ActionInput.builder()
																.name("token")
																.type(ActionInputType.TEXT)
																.sensitive(true)
																.build()))
										.build())
						.build();
		ActionInvocationDialog password = new ActionInvocationDialog(run, sensitive);
		WindowTestSupport.find(password.createCenterPanel(), JPasswordField.class)
				.setText("private-value");
		password.close(0);
		assertTrue(run.actionDraft(sensitive).isEmpty());
		assertTrue(history.history(key("PLAYER:alice", "fixture.secret:token")).isEmpty());
	}

	public void testGeneratedActionFormRendersScalarControlsInBothThemes() throws Exception {
		for (boolean dark : List.of(true, false)) {
			WindowTestSupport.useTheme(getTestRootDisposable(), dark);
			ActionDescriptor descriptor =
					action("fixture.form").toBuilder()
							.definition(
									ActionDefinition.builder()
											.id("fixture.form")
											.displayName("Inspect replication")
											.description("Inspect a key on the selected target.")
											.input(
													ActionInput.builder()
															.name("key")
															.displayName("Redis key")
															.type(ActionInputType.TEXT)
															.defaultValue("player:External")
															.build())
											.input(
													ActionInput.builder()
															.name("amount")
															.displayName("Amount")
															.type(ActionInputType.INTEGER)
															.defaultValue("2")
															.build())
											.input(
													ActionInput.builder()
															.name("factor")
															.displayName("Factor")
															.type(ActionInputType.DECIMAL)
															.defaultValue("1.5")
															.build())
											.input(
													ActionInput.builder()
															.name("details")
															.displayName("Include details")
															.type(ActionInputType.BOOLEAN)
															.defaultValue("true")
															.build())
											.input(
													ActionInput.builder()
															.name("replica")
															.displayName("Read from")
															.type(ActionInputType.CHOICE)
															.choices(List.of("primary", "replica"))
															.defaultValue("replica")
															.build())
											.input(
													ActionInput.builder()
															.name("token")
															.displayName("Token")
															.type(ActionInputType.TEXT)
															.required(false)
															.sensitive(true)
															.build())
											.build())
							.build();
			ActionInvocationDialog dialog = new ActionInvocationDialog(run, descriptor);
			assertNull(dialog.doValidate());
			WindowTestSupport.capture(
					dialog.createCenterPanel(),
					"tooling-action-" + (dark ? "dark" : "light") + ".png",
					620,
					Math.max(360, dialog.createCenterPanel().getPreferredSize().height));
			dialog.close(0);
		}
	}

	private ActionDescriptor action(String id) {
		return ActionDescriptor.builder()
				.definition(
						ActionDefinition.builder()
								.id(id)
								.displayName(id)
								.inputs(
										List.of(ActionInput.builder().name("text").type(ActionInputType.TEXT).build()))
								.build())
				.target(ActionTarget.builder().type(ActionTargetType.PLAYER).name("alice").build())
				.availability(ActionAvailability.builder().enabled(true).build())
				.build();
	}

	private CommandInput input() {
		CommandInput input = new CommandInput(run, "Console command");
		input.scope("paper", "console");
		input.onSubmit(input::submit);
		return input;
	}

	private CommandScope key(String target, String operation) {
		return CommandScope.builder()
				.source(source.getId())
				.definition(scenario.getDefinition())
				.scenario(scenario.getName())
				.target(target)
				.operation(operation)
				.build();
	}

	private long submittedCount() {
		return runner.getRequests().stream()
				.filter(
						request -> List.of("console", "action").contains(request.path("operation").asText()))
				.count();
	}

	private String consoleText() {
		ConsoleViewImpl console = (ConsoleViewImpl) presentation.getConsole();
		console.getComponent();
		console.waitAllRequests();
		return console.getEditor().getDocument().getText();
	}

	private static final class HistoryProvider implements BuildIntegration {
		private final ScenarioSource source;

		private HistoryProvider(ScenarioSource source) {
			this.source = source;
		}

		@Override
		public @NotNull String getId() {
			return "history-fixture";
		}

		@Override
		public @NotNull SourceListing discover(@NotNull Project project) {
			return SourceListing.builder()
					.status(SourceListingStatus.READY)
					.message("Ready")
					.sources(List.of(source))
					.build();
		}

		@Override
		public boolean canSync(@NotNull Project project) {
			return false;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void subscribe(
				@NotNull Project project,
				@NotNull Consumer<ProjectChange> listener,
				@NotNull Disposable owner) {}

		@Override
		public @NotNull ScenarioPreparation prepare(
				@NotNull Project project, @NotNull ScenarioSource selected) throws IOException {
			Path manifest = Files.createTempFile(source.getDirectory(), "anvil-history-", ".json");
			Files.writeString(
					manifest,
					"{\"schemaVersion\":1,\"toolingJavaExecutable\":\"history-java\",\"classpath\":[\"runtime\"],\"definitions\":[\"example.AuthenticationScenarios\"]}");
			return ScenarioPreparation.builder()
					.command(List.of("history-prepare"))
					.workingDirectory(source.getDirectory())
					.manifestPath(manifest)
					.build();
		}
	}
}
