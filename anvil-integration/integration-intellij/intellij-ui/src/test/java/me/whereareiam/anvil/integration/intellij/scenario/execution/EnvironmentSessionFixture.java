package me.whereareiam.anvil.integration.intellij.scenario.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.log.StoredSessionLog;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.tooling.ToolingLaunch;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies real run presentation for UI tests without preparing or launching a runtime.
 */
@UtilityClass
public class EnvironmentSessionFixture {
	private static final Map<RetainedEnvironmentSession, ConsolePresentation> PRESENTATIONS = new WeakHashMap<>();

	/**
	 * Provides stable opaque IDs for named test executions without depending on runtime counters.
	 */
	public static @NotNull UUID executionId(@NotNull String name) {
		return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
	}


	public static @NotNull RetainedEnvironmentSession create(
			@NotNull Project project,
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@NotNull Disposable owner) {
		return create(project, source, scenario, owner, true);
	}

	public static @NotNull RetainedEnvironmentSession create(
			@NotNull Project project,
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@NotNull Disposable owner,
			boolean notifyPresentation) {
		RetainedEnvironmentSession run =
				new RetainedEnvironmentSession(
						project, new ToolingLaunch(source, ToolingLaunch.Inputs.of(project), ProcessBuilder::start), scenario, null, () -> {}, () -> {});
		Disposer.register(owner, run);
		var presentation = new ConsolePresentation(project, run.getLog(), run::stop, run);
		synchronized (PRESENTATIONS) {
			PRESENTATIONS.put(run, presentation);
		}
		if (notifyPresentation) presentation.start();

		return run;
	}

	/**
	 * Returns the console the fixture created for a session; views create their own presentations.
	 */
	public static @NotNull ConsolePresentation presentation(@NotNull RetainedEnvironmentSession run) {
		synchronized (PRESENTATIONS) {
			return PRESENTATIONS.get(run);
		}
	}

	/**
	 * Decodes exported scenario fixtures using the same protocol parser as the IDE session.
	 */
	public static @NotNull List<ScenarioDescriptor> scenarios(@NotNull JsonNode payload) {
		var mapper = new ObjectMapper();
		return mapper.convertValue(payload, mapper.getTypeFactory().constructCollectionType(List.class, ScenarioDescriptor.class));
	}

	/**
	 * Reads actual ConsoleView token attributes after pending output has reached the editor document.
	 */
	public static @NotNull TextAttributes attributes(
			@NotNull ConsoleViewImpl console, @NotNull String marker) {
		console.waitAllRequests();
		var editor = console.getEditor();
		int offset = editor.getDocument().getText().indexOf(marker);
		if (offset < 0) throw new AssertionError("Missing rendered text: " + marker);
		var markup = DocumentMarkupModel.forDocument(editor.getDocument(), console.getProject(), false);
		for (RangeHighlighter highlighter : markup.getAllHighlighters())
			if (highlighter.getStartOffset() <= offset && highlighter.getEndOffset() > offset) {
				TextAttributes attributes = highlighter.getTextAttributes(editor.getColorsScheme());
				if (attributes != null) return attributes;
			}
		throw new AssertionError("Missing actual ConsoleView attributes for " + marker);
	}

	/**
	 * Reports whether the retained handle was disposed, for example by closing its tab.
	 */
	public static boolean disposed(@NotNull RetainedEnvironmentSession session) {
		return session.isDisposed();
	}

	public static void update(
			@NotNull RetainedEnvironmentSession run, @NotNull SessionSnapshot snapshot) {
		run.update(snapshot);
	}

	public static void scenario(@NotNull RetainedEnvironmentSession run, @NotNull ScenarioDescriptor scenario) {
		run.scenario(scenario);
	}

	public static void append(
			@NotNull RetainedEnvironmentSession run, @Nullable String process, @NotNull String text, boolean error) {
		run.append(process, text, error);
	}

	public static void clear(@NotNull RetainedEnvironmentSession run) {
		((StoredSessionLog) run.getLog()).clear();
	}

	public static void finish(@NotNull RetainedEnvironmentSession run, int exitCode) {
		run.finished(exitCode);
	}
}
