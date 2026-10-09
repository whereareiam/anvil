package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewRunningState;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;

import java.awt.Color;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

public class AnsiConsolePlatformTest extends UiPlatformTestCase {
	public void testNativeConsoleUsesEmittedColorsAndResetsWithoutColoringPlainSeverityText() {
		RetainedEnvironmentSession run = createRun();
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-1"),
				1,
				"Plain ERROR stays plain\n\u001b[31mRed output\u001b[0m normal again\n",
				false);
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-1"),
				2,
				"\u001b[38;2;12;34;56mTrue color\u001b[0m\n",
				false);
		ConsoleViewImpl console = console(run);
		assertFalse(text(console).contains("\u001b"));
		assertColor(console, "Red output", ConsoleHighlighter.RED);
		assertColor(console, "normal again", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		assertColor(console, "Plain ERROR", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		assertColor(console, "[lobby]", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		assertEquals(new Color(12, 34, 56), attributes(console, "True color").getForegroundColor());
	}

	public void testProcessAndOutputStreamsKeepIndependentColorState() {
		RetainedEnvironmentSession run = createRun();
		run.append("lobby", "\u001b[32mLobby green\n", false);
		run.append("proxy", "Proxy plain\n", false);
		run.append("lobby", "Unstyled failure on stderr\n", true);
		run.append("lobby", "\u001b[34mBlue stderr\n", true);
		run.append("lobby", "Still green stdout\n", false);
		run.append(null, "\u001b[35mCommon stdout\n", false);
		run.append(null, "Runtime failed\n", true);
		ConsoleViewImpl console = console(run);
		assertColor(console, "Lobby green", ConsoleHighlighter.GREEN);
		assertColor(console, "Proxy plain", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		assertColor(console, "Unstyled failure", ConsoleViewContentType.ERROR_OUTPUT_KEY);
		assertColor(console, "Blue stderr", ConsoleHighlighter.BLUE);
		assertColor(console, "Still green", ConsoleHighlighter.GREEN);
		assertColor(console, "Runtime failed", ConsoleViewContentType.ERROR_OUTPUT_KEY);
	}

	public void testSplitEscapeSequencesSurviveInterleavedSourcesWithoutOrphanPrefixes() {
		RetainedEnvironmentSession run = createRun();
		run.append("lobby", "\u001b[3", false);
		run.append("proxy", "Proxy between chunks\n", false);
		run.append("lobby", "1mCompleted red sequence\n\u001b[", false);
		run.append("lobby", "0mReset completed\n", false);
		ConsoleViewImpl console = console(run);
		String rendered = text(console);
		assertTrue(rendered.startsWith("[proxy] Proxy between chunks\n[lobby] Completed red sequence"));
		assertFalse(rendered.contains("\u001b"));
		assertColor(console, "Completed red", ConsoleHighlighter.RED);
		assertColor(console, "Proxy between", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		assertColor(console, "Reset completed", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
	}

	public void testFilteredReplayKeepsColorsAfterTheOriginalEscapeLeavesBoundedHistory() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionFixture.presentation(run).setProcessFilter("lobby");
		run.append("lobby", "\u001b[32mOriginal color anchor\n", false);
		String filler = "Other process output ".repeat(3000) + "\n";
		for (int index = 0; index < 35; index++) run.append("proxy", filler, false);
		run.append("lobby", "Color survives history eviction\n", false);
		run.append(null, "Common diagnostic remains\n", false);
		EnvironmentSessionFixture.presentation(run).setProcessFilter("lobby");
		ConsoleViewImpl console = console(run);
		String replay = text(console);
		assertTrue(replay.contains("Earlier output is no longer retained."));
		assertFalse(replay.contains("Original color anchor"));
		assertFalse(replay.contains("Other process output"));
		assertTrue(replay.contains("Common diagnostic remains"));
		assertColor(console, "Color survives", ConsoleHighlighter.GREEN);
		EnvironmentSessionFixture.presentation(run).setProcessFilter("proxy");
		EnvironmentSessionFixture.presentation(run).setProcessFilter("lobby");
		assertEquals(replay, text(console));
		assertColor(console, "Color survives", ConsoleHighlighter.GREEN);
	}

	public void testOversizedDiscardedTailStillAppliesItsActualColorReset() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionFixture.presentation(run).setProcessFilter("proxy");
		run.append("lobby", "\u001b[31m" + "x".repeat(150_000) + "\u001b[0m\n", false);
		run.append("lobby", "Plain after discarded reset\n", false);
		EnvironmentSessionFixture.presentation(run).setProcessFilter("lobby");
		ConsoleViewImpl console = console(run);
		assertTrue(text(console).contains("[Output entry truncated.]"));
		assertTrue("A large single entry stays bounded", text(console).length() < 70_000);
		assertColor(console, "Plain after discarded", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		run.append("lobby", "\u001b[31m" + "x".repeat(150_000) + "\u001b[32m\n", false);
		run.append("lobby", "Green from discarded tail\n", false);
		assertColor(console, "Green from discarded", ConsoleHighlighter.GREEN);
	}

	public void testGenerationsAndUpstreamSequenceGapsResetOnlyTheirOwnDecoder() {
		RetainedEnvironmentSession run = createRun();
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-1"),
				1,
				"\u001b[31mOld generation\n",
				false);
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-2"),
				1,
				"Replacement starts plain\n",
				false);
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-1"),
				2,
				"Old cleanup stays red\n",
				false);
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-2"),
				2,
				"\u001b[32mNew generation green\n",
				false);
		run.append(
				"lobby",
				EnvironmentSessionFixture.executionId("lobby-2"),
				10,
				"Missing lines reset unknown style\n",
				false);
		ConsoleViewImpl console = console(run);
		assertColor(console, "Replacement starts", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		assertColor(console, "Old cleanup", ConsoleHighlighter.RED);
		assertColor(console, "New generation green", ConsoleHighlighter.GREEN);
		assertColor(console, "Missing lines", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
		EnvironmentSessionFixture.presentation(run).setProcessFilter("lobby");
		assertColor(console, "Old cleanup", ConsoleHighlighter.RED);
		assertColor(console, "New generation green", ConsoleHighlighter.GREEN);
	}

	public void testRunWindowConsoleKeepsColoredCleanupAfterAnvilViewCloses() {
		RetainedEnvironmentSession run = createRun();
		// A Run window presentation is owned by the project, not by the Anvil tab's session.
		var runWindow = new ConsolePresentation(getProject(), run.getLog(), run::stop, getTestRootDisposable());
		ConsoleViewImpl adapter = (ConsoleViewImpl) runWindow.getConsole();
		adapter.getComponent();
		List<Key<?>> errorTypes = new ArrayList<>();
		runWindow.getProcessHandler()
				.addProcessListener(
						new ProcessAdapter() {
							@Override
							public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
								if (event.getText().contains("Cleanup failed")) errorTypes.add(outputType);
							}
						});
		run.append(null, "\u001b[32mLive output\n", false);
		Disposer.dispose(run);
		run.append(null, "Cleanup continues green\n", false);
		run.append(null, "Cleanup failed\n", true);
		EnvironmentSessionFixture.finish(run, 1);
		assertTrue(text(adapter).contains("Cleanup continues green"));
		assertFalse(text(adapter).contains("\u001b"));
		assertColor(adapter, "Cleanup continues", ConsoleHighlighter.GREEN);
		assertColor(adapter, "Cleanup failed", ConsoleViewContentType.ERROR_OUTPUT_KEY);
		assertEquals(1, errorTypes.size());
		assertTrue(ProcessOutputType.isStderr(errorTypes.getFirst()));
	}

	public void testColoredStackTraceRetainsJavaSourceHyperlinks() {
		myFixture.addFileToProject(
				"example/Failure.java",
				"package example; public class Failure { public static void crash() { throw new"
						+ " IllegalStateException(); } }");
		RetainedEnvironmentSession run = createRun();
		run.append("lobby", "\u001b[31mjava.lang.IllegalStateException: boom\u001b[0m\n", true);
		run.append("lobby", "\u001b[31m\tat example.Failure.crash(Failure.java:1)\u001b[0m\n", true);
		ConsoleViewImpl console = console(run);
		String rendered = text(console);
		assertTrue(rendered.contains("at example.Failure.crash(Failure.java:1)"));
		console.getHyperlinks().waitForPendingFilters(5000);
		assertNotNull(
				"ANSI must not disrupt exception navigation",
				console.getHyperlinks().getHyperlinkAt(rendered.indexOf("Failure.java:1")));
	}

	public void testDecodesRealTerminalConsoleAppenderFormatting() throws Exception {
		// Captured from actual TCA 1.3.0 HighlightErrorConverter and MinecraftFormattingConverter in a
		// piped JVM.
		String source;
		try (var input =
				getClass().getResourceAsStream("/session/terminal-console-appender-1.3.0-ansi.txt")) {
			assertNotNull(input);
			source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		RetainedEnvironmentSession run = createRun();
		run.append(null, source, false);
		ConsoleViewImpl console = console(run);
		assertEquals("ERROR Backend connection refused\nBackend ready\n", text(console));
		assertColor(console, "ERROR Backend", ConsoleHighlighter.RED);
		assertColor(console, "Backend ready", ConsoleHighlighter.GREEN);
		assertTrue((attributes(console, "ERROR Backend").getFontType() & Font.BOLD) != 0);
	}

	private RetainedEnvironmentSession createRun() {
		var project =
				ScenarioSource.builder()
						.id("fixture:ansi")
						.displayName("ANSI fixture")
						.integrationId("fixture")
						.directory(Path.of(getProject().getBasePath()))
						.build();
		var scenario =
				ScenarioDescriptor.builder()
						.definition("example.Scenarios")
						.name("ansi")
						.displayName("ANSI fixture")
						.build();
		RetainedEnvironmentSession run =
				EnvironmentSessionFixture.create(getProject(), project, scenario, getTestRootDisposable());
		EnvironmentSessionFixture.presentation(run).getConsole().getComponent();
		return run;
	}

	private static ConsoleViewImpl console(RetainedEnvironmentSession run) {
		return (ConsoleViewImpl) EnvironmentSessionFixture.presentation(run).getConsole();
	}

	private static String text(ConsoleViewImpl console) {
		if (console.getState() instanceof ConsoleViewRunningState running)
			running.getStreamsSynchronizer().waitForAllFlushed();
		console.waitAllRequests();
		return console.getEditor().getDocument().getText();
	}

	private void assertColor(ConsoleViewImpl console, String marker, TextAttributesKey key) {
		text(console);
		Color expected = console.getEditor().getColorsScheme().getAttributes(key).getForegroundColor();
		if (expected == null) expected = console.getEditor().getColorsScheme().getDefaultForeground();
		Color actual = attributes(console, marker).getForegroundColor();
		if (actual == null) actual = console.getEditor().getColorsScheme().getDefaultForeground();
		assertEquals("Native foreground for " + marker, expected, actual);
	}

	private static TextAttributes attributes(ConsoleViewImpl console, String marker) {
		return EnvironmentSessionFixture.attributes(console, marker);
	}
}
