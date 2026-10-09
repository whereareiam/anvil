package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

public class SessionConsolePlatformTest extends UiPlatformTestCase {
	public void testPreparationConsoleDecodesAnsiAndRefreshStartsWithFreshColorState() {
		RetainedEnvironmentSession session = session();
		var console = (ConsoleViewImpl) EnvironmentSessionFixture.presentation(session).getConsole();
		console.getComponent();
		EnvironmentSessionFixture.append(session, null, "\u001b[32mGreen preparation\n", false);
		assertFalse(text(console).contains("\u001b"));
		assertEquals(
				console.getEditor().getColorsScheme().getAttributes(ConsoleHighlighter.GREEN).getForegroundColor(),
				EnvironmentSessionFixture.attributes(console, "Green preparation").getForegroundColor());
		EnvironmentSessionFixture.clear(session);
		EnvironmentSessionFixture.append(session, null, "Fresh preparation is plain\n", false);
		assertEquals("Fresh preparation is plain\n", text(console));
		assertEquals(
				console.getEditor().getColorsScheme()
						.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).getForegroundColor(),
				EnvironmentSessionFixture.attributes(console, "Fresh preparation").getForegroundColor());
		EnvironmentSessionFixture.finish(session, 0);
	}

	public void testRunConsoleFilterPreservesCommonMessagesAndRestoresMergedOutput() {
		ScenarioSource source = ScenarioSource.builder().id("fixture").integrationId("fixture")
				.displayName("Example").directory(Path.of("/project")).build();
		var scenario = ScenarioDescriptor.builder().definition("example.Definition").name("scenario").displayName("Scenario").build();
		RetainedEnvironmentSession run = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		var presentation = EnvironmentSessionFixture.presentation(run);
		var console = (ConsoleViewImpl) presentation.getConsole();
		console.getComponent();
		run.append(null, "Preparation complete\n", false);
		run.append("lobby", "Lobby output\n", false);
		run.append("game", "Game output\n", false);
		presentation.setProcessFilter("lobby");
		assertTrue(text(console).contains("Preparation complete"));
		assertTrue(text(console).contains("Lobby output"));
		assertFalse(text(console).contains("Game output"));
		presentation.setProcessFilter(null);
		assertTrue(text(console).contains("Game output"));
		EnvironmentSessionFixture.finish(run, 0);
	}

	public void testNativeExecutionViewPublishesOneStartNotification() {
		RetainedEnvironmentSession session = session(false);
		var handler = EnvironmentSessionFixture.presentation(session).getProcessHandler();
		AtomicInteger notifications = new AtomicInteger();
		handler.addProcessListener(new ProcessAdapter() {
			@Override public void startNotified(@NotNull ProcessEvent event) { notifications.incrementAndGet(); }
		});
		handler.startNotify();
		assertEquals(1, notifications.get());
		EnvironmentSessionFixture.finish(session, 0);
		assertTrue(handler.isProcessTerminated());
	}

	private RetainedEnvironmentSession session() {
		return session(true);
	}

	private RetainedEnvironmentSession session(boolean notifyPresentation) {
		ScenarioSource source = ScenarioSource.builder()
				.id("fixture")
				.integrationId("fixture")
				.displayName("Example")
				.directory(Path.of("/project"))
				.build();
		ScenarioDescriptor scenario = ScenarioDescriptor.builder()
				.definition("example.Definition")
				.name("scenario")
				.displayName("Scenario")
				.build();
		return EnvironmentSessionFixture.create(
				getProject(), source, scenario, getTestRootDisposable(), notifyPresentation);
	}

	private static String text(ConsoleViewImpl console) {
		console.waitAllRequests();
		return console.getEditor().getDocument().getText();
	}
}
