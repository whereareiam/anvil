package me.whereareiam.anvil.integration.intellij.component.console;

import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import java.io.OutputStream;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.log.SessionLogListener;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders one session log in one native console for one view.
 * <p>
 * Each view (a tool-window tab, a Run tab, or a dialog) creates its own presentation. Views share only the
 * session log, which replays retained output to each new subscriber, so disposing one view never affects
 * another. The presentation ends with its owner, or earlier when the Run window disposes its console.
 */
public final class ConsolePresentation implements SessionLogListener, Disposable {
	@Getter
	private final @NotNull ConsoleView console;
	private final @NotNull ConsoleProcessHandler handler;

	private @NotNull ConsoleBuffer buffer = new ConsoleBuffer();
	private @Nullable String processFilter;
	private boolean disposed;
	private boolean finished;

	/**
	 * Creates a console for one view and subscribes it to the session log.
	 *
	 * @param stop requests the session to stop when the native Stop action is used
	 * @param owner ends the presentation and its console when disposed
	 */
	public ConsolePresentation(
			@NotNull Project project,
			@NotNull SessionLog log,
			@NotNull Runnable stop,
			@NotNull Disposable owner
	) {
		console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
		console.addMessageFilter(new ExceptionFilter(project, GlobalSearchScope.projectScope(project)));
		handler = new ConsoleProcessHandler(stop);
		Disposer.register(owner, console);
		Disposer.register(console, this);
		// The subscription is a child of this presentation, so disposal releases it before dispose() takes
		// this presentation's lock. The log therefore never waits for this lock while holding its own.
		log.subscribe(this, this);
	}

	/**
	 * Returns the native handler that mirrors session output and termination for this view.
	 */
	public @NotNull ProcessHandler getProcessHandler() {
		return handler;
	}

	/**
	 * Starts the native process presentation once.
	 */
	public void start() {
		if (!handler.isStartNotified()) handler.startNotify();
	}

	/**
	 * Replays retained output for one process, or for all processes when the name is null.
	 */
	@RequiresEdt
	public synchronized void setProcessFilter(@Nullable String processName) {
		if (disposed) return;

		ApplicationManager.getApplication().assertIsDispatchThread();
		// Clearing and replaying require the lazily created console editor.
		console.getComponent();
		processFilter = processName;
		console.clear();
		buffer.replay(processName, this::print);
	}

	@Override
	public synchronized void cleared() {
		if (disposed) return;

		console.clear();
		buffer = new ConsoleBuffer();
	}

	@Override
	public synchronized void appended(@NotNull SessionLogEntry entry) {
		if (disposed || finished) return;

		var chunks = buffer.append(
				entry.getProcess(),
				entry.getExecutionId(),
				entry.getSequence(),
				entry.getText(),
				entry.isError());
		for (var chunk : chunks) {
			handler.notifyTextAvailable(chunk.text(), chunk.attributes());
			if (visible(entry.getProcess())) print(chunk);
		}
	}

	@Override
	public synchronized void finished(int exitCode) {
		if (finished) return;

		finished = true;
		handler.finished(exitCode);
	}

	@Override
	public synchronized void dispose() {
		disposed = true;
		buffer.clear();
	}

	private boolean visible(@Nullable String process) {
		return processFilter == null || process == null || processFilter.equals(process);
	}

	private void print(ConsoleBuffer.Chunk chunk) {
		console.print(chunk.text(), ConsoleViewContentType.getConsoleViewType(chunk.attributes()));
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class ConsoleProcessHandler extends ProcessHandler {
		private final @NotNull Runnable stop;

		@Override
		protected void destroyProcessImpl() {
			stop.run();
		}

		@Override
		protected void detachProcessImpl() {
			stop.run();
		}

		@Override
		public boolean detachIsDefault() {
			return false;
		}

		@Override
		public @Nullable OutputStream getProcessInput() {
			return null;
		}

		private void finished(int exitCode) {
			notifyProcessTerminated(exitCode);
		}
	}
}
