package me.whereareiam.anvil.runner;


import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistry;
import me.whereareiam.anvil.runner.scenario.DisplayNameResolver;
import me.whereareiam.anvil.runner.session.SessionConsole;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.runner.extension.ToolingExtensionRegistry;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Owns an engine and its replaceable foreground environment for terminal and IDE clients.
 * Mutations are serialized; snapshots remain readable during blocking startup and cleanup.
 */
public final class RunnerSession implements ToolingSession {

	private final Supplier<ScenarioEngine> engineFactory;
	private final ToolingRegistry extensions;
	private @Nullable ScenarioEngine engine;
	private final ScenarioRepository scenarios;
	private final ReentrantLock lifecycle = new ReentrantLock();
	private volatile SessionSnapshot latest = SessionSnapshot.builder().state(SessionState.IDLE).build();
	private @Nullable ScenarioContext context;
	private boolean closed;
	private final SessionConsole consoleOutput;

	/**
	 * Retains a lazy engine factory and definitions previously evaluated on the consumer classpath.
	 *
	 * @param engineFactory creates the engine at the first start; this owner closes the result
	 * @param definitions fully qualified definition class names; an empty list discovers the index
	 */
	public RunnerSession(
			@NotNull Supplier<ScenarioEngine> engineFactory,
			@NotNull List<String> definitions
	) throws ReflectiveOperationException {
		this(engineFactory, ScenarioRepository.load(definitions), ToolingExtensionRegistry.discover());
	}

	/**
	 * Creates a session from a repository supplied by an embedding host or test.
	 *
	 * @param engineFactory creates the owned engine on first start
	 * @param scenarios evaluated definition repository
	 */
	public RunnerSession(
			@NotNull Supplier<ScenarioEngine> engineFactory,
			@NotNull ScenarioRepository scenarios
	) {
		this(engineFactory, scenarios, ToolingExtensionRegistry.discover());
	}

	/**
	 * Creates a session with an explicitly supplied tooling registry. This is the embedding and
	 * testing seam for hosts that discover or compose tooling contributions themselves.
	 *
	 * @param engineFactory creates the owned engine on first start
	 * @param scenarios evaluated definition repository
	 * @param extensions registry resolving actions and observations for active contexts
	 */
	public RunnerSession(
			@NotNull Supplier<ScenarioEngine> engineFactory,
			@NotNull ScenarioRepository scenarios,
			@NotNull ToolingRegistry extensions
	) {
		this.engineFactory = engineFactory;
		this.scenarios = scenarios;
		this.extensions = extensions;
		this.consoleOutput = new SessionConsole(() -> latest.getSessionId());
	}

	@Override
	public @NotNull List<ScenarioDescriptor> scenarios() {
		return scenarios.scenarios();
	}

	@Override
	public void start(@NotNull String definition, @Nullable String process) {
		lifecycle.lock();
		try {
			ensureOpen();
			// The process only narrows the start; it never selects a scenario.
			ScenarioRepository.Entry selected = scenarios.require(definition);
			AnvilScenario scenario = selected.scenario();
			if (process != null) requireDeclaredProcess(scenario, process);
			stop();

			Map<String, PresentationMetadata> labels = new LinkedHashMap<>();
			Stream.concat(scenario.getServers().stream(), scenario.getProxies().stream())
					.filter(component -> component.getMetadata() != null)
					.forEach(component -> labels.put(component.getName(), component.getMetadata()));
			consoleOutput.beginRun(labels);
			latest = SessionSnapshot.builder()
					.sessionId(UUID.randomUUID().toString())
					.definition(selected.definition())
					.scenario(scenario.getName())
					.entrypoint(scenario.getEntrypoint())
					.displayName(DisplayNameResolver.resolve(scenario.getName(), scenario.getMetadata()))
					.state(SessionState.STARTING)
					.build();
			try {
				if (engine == null) engine = engineFactory.get();
				context = engine.prepare(scenario, consoleOutput::processCreated);
				if (process == null) context.start();
				else context.processes().start(process);
				latest = latest.toBuilder().state(SessionState.RUNNING).setupComplete(process == null).build();
			} catch (RuntimeException | Error failure) {
				ScenarioContext acquired = context;
				if (acquired == null) startupFailed(failure);
				else finishFailed(acquired, failure);
				throw failure;
			}

			// Projecting the snapshot is not part of the lifecycle; its failures must not finalize the environment.
			refresh();
		} finally {
			lifecycle.unlock();
		}
	}

	@Override
	public void startAll() {
		mutateProcesses(null, SessionState.STARTING, current -> {
			current.start();
			latest = latest.toBuilder().setupComplete(true).build();
		});
	}

	@Override
	public void startProcess(@NotNull String process) {
		mutateProcesses(process, SessionState.STARTING, current -> current.processes().start(process));
	}

	@Override
	public void stopProcess(@NotNull String process) {
		mutateProcesses(process, SessionState.RUNNING, current -> current.processes().stop(process));
	}

	private void mutateProcesses(@Nullable String process, SessionState progress, Consumer<ScenarioContext> action) {
		lifecycle.lock();
		try {
			ScenarioContext current = requireRunning();
			if (process != null) requireDeclaredProcess(current.definition(), process);
			latest = latest.toBuilder().state(progress).build();
			try {
				action.accept(current);
				latest = latest.toBuilder().state(SessionState.RUNNING).build();
			} catch (RuntimeException | Error failure) {
				finishFailed(current, failure);
				throw failure;
			}

			refresh();
		} finally {
			lifecycle.unlock();
		}
	}

	private static void requireDeclaredProcess(AnvilScenario definition, String process) {
		if (Stream.concat(definition.getServers().stream(), definition.getProxies().stream())
				.noneMatch(candidate -> candidate.getName().equals(process)))
			throw new NoSuchElementException("Unknown process: " + process);
	}

	@Override
	public @NotNull SessionSnapshot snapshot() {
		if (!lifecycle.tryLock()) return latest.toBuilder().processes(consoleOutput.descriptors()).build();
		try {
			refresh();
			return latest.toBuilder().processes(consoleOutput.descriptors()).build();
		} finally {
			lifecycle.unlock();
		}
	}

	@Override
	public void console(@NotNull String process, @NotNull String command) {
		validateText(command);
		lifecycle.lock();
		try {
			RunningProcess target = requireRunning().processes().get(process);
			if (target.state() != me.whereareiam.anvil.api.type.ProcessState.READY) throw new IllegalStateException("Process is not ready: " + process);
			target.console().sendCommand(command);
		} finally {
			lifecycle.unlock();
		}
	}

	@Override
	public @NotNull ActionResult invoke(@NotNull ActionRequest request) {
		lifecycle.lock();
		try {
			ScenarioContext current = requireRunning();
			if (!request.getSessionId().equals(latest.getSessionId()))
				throw new IllegalStateException("Action belongs to a different environment");
			ActionResult result = extensions.invoke(current, request);
			refresh();
			return result;
		} finally {
			lifecycle.unlock();
		}
	}

	@Override
	public void restartProcess(@NotNull String process) {
		lifecycle.lock();
		try {
			ScenarioContext current = requireRunning();
			current.processes().get(process);
			try {
				current.processes().restart(process);
			} catch (RuntimeException | Error failure) {
				finishFailed(current, failure);
				throw failure;
			}

			refresh();
		} finally {
			lifecycle.unlock();
		}
	}

	/**
	 * Returns retained tail output without consuming the event stream.
	 *
	 * @param process stable process name
	 * @param maximumLines tail limit
	 * @return captured output
	 */
	public @NotNull List<String> logs(@NotNull String process, int maximumLines) {
		lifecycle.lock();
		try { return consoleOutput.tail(process, maximumLines); }
		finally { lifecycle.unlock(); }
	}

	@Override
	public @NotNull List<LogEvent> drainLogs() { return consoleOutput.drain(); }

	@Override
	public void stop() {
		lifecycle.lock();
		try {
			if (context == null) return;
			ScenarioContext current = context;
			latest = latest.toBuilder().state(SessionState.STOPPING).build();
			try {
				current.finish(latest.getFailure() == null);
				latest = latest.toBuilder().state(latest.getFailure() == null ? SessionState.STOPPED : SessionState.FAILED)
						.processes(latest.getProcesses().stream().map(process -> process.toBuilder().state(me.whereareiam.anvil.tooling.api.type.ProcessState.STOPPED).build()).toList())
						.players(List.of()).actions(List.of()).observations(List.of()).build();
			} catch (RuntimeException | Error failure) {
				fail(failure);
				throw failure;
			} finally {
				context = null;
			}
		} finally {
			lifecycle.unlock();
		}
	}

	@Override
	public void close() {
		lifecycle.lock();
		try {
			if (closed) return;
			closed = true;
			try (ScenarioEngine owned = engine) {
				stop();
			}
		} finally {
			lifecycle.unlock();
		}
	}

	private ScenarioContext requireRunning() {
		ensureOpen();
		if (context == null || latest.getState() != SessionState.RUNNING)
			throw new IllegalStateException("No scenario is ready for commands");
		return context;
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Tooling session is closed");
	}

	private void refresh() {
		if (context == null) return;
		context.processes().all().forEach(consoleOutput::processCreated);
		List<ProcessSnapshot> processes = consoleOutput.descriptors();
		List<PlayerDescriptor> players = context.players().all().stream().map(player -> PlayerDescriptor.builder()
				.name(player.name()).displayName(DisplayNameResolver.resolve(player.name(), player.metadata()))
				.build()).toList();
		latest = latest.toBuilder().processes(processes).players(players)
				.actions(extensions.actions(context)).observations(extensions.observations(context)).build();
		if (latest.getState() == SessionState.RUNNING && processes.stream().anyMatch(process -> process.getState() == me.whereareiam.anvil.tooling.api.type.ProcessState.FAILED))
			latest = latest.toBuilder().state(SessionState.FAILED).failure("A scenario process failed; inspect retained logs.").build();
	}

	private void startupFailed(Throwable failure) {
		if (cancelled(failure)) {
			latest = latest.toBuilder().state(SessionState.STOPPED).failure(null).build();
			return;
		}
		fail(failure);
	}

	private void finishFailed(ScenarioContext failed, Throwable failure) {
		context = null;
		try {
			failed.finish(false);
		} catch (RuntimeException | Error cleanup) {
			if (cleanup != failure) failure.addSuppressed(cleanup);
		}
		startupFailed(failure);
	}

	private static boolean cancelled(Throwable failure) {
		boolean interrupted = false;
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause.getSuppressed().length != 0) return false;
			if (cause instanceof InterruptedException || cause instanceof CancellationException) interrupted = true;
		}
		return interrupted;
	}

	private void fail(Throwable failure) {
		StringWriter text = new StringWriter();
		failure.printStackTrace(new PrintWriter(text));
		latest = latest.toBuilder().state(SessionState.FAILED).failure(text.toString()).build();
	}

	private static void validateText(String text) {
		if (text.isBlank() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
			throw new IllegalArgumentException("Supply exactly one non-blank command or message");
	}

}
