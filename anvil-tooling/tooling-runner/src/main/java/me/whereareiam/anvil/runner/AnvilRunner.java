package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.runner.command.InteractiveSession;
import me.whereareiam.anvil.runner.command.RunnerArgumentsParser;
import me.whereareiam.anvil.runner.command.RunnerTerminal;
import me.whereareiam.anvil.runner.model.command.RunnerArguments;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reusable foreground command-line runner for directly discoverable Anvil scenarios.
 * The runner is independent of Gradle and receives an engine factory from its host.
 */
public final class AnvilRunner {
	private final Supplier<ScenarioEngine> engineFactory;
	private final Reader input;
	private final RunnerTerminal terminal;

	/**
	 * Creates a runner backed by the supplied terminal streams.
	 *
	 * @param input command input
	 * @param output user-facing command output
	 * @param engineFactory creates an owned engine on the first scenario start; listing does not call it
	 */
	public AnvilRunner(
			@NotNull Reader input,
			@NotNull PrintWriter output,
			@NotNull Supplier<ScenarioEngine> engineFactory
	) {
		this.engineFactory = engineFactory;
		this.input = Objects.requireNonNull(input, "input");
		this.terminal = new RunnerTerminal(Objects.requireNonNull(output, "output"));
	}

	/**
	 * Runs the scenario CLI using the supplied engine factory and borrowed terminal streams.
	 * Definitions are read from the generated index unless a direct definition class is selected.
	 *
	 * @param arguments command-line arguments
	 * @throws Exception when definition loading or scenario execution fails
	 */
	public void run(@NotNull String[] arguments) throws Exception {
		RunnerArguments parsed = RunnerArgumentsParser.parse(arguments);
		ScenarioRepository repository = parsed.getDefinition() == null
				? ScenarioRepository.discover()
				: ScenarioRepository.load(List.of(parsed.getDefinition()));
		if (parsed.isList()) {
			terminal.showScenarios(repository.scenarios());
			return;
		}

		String selection = parsed.getDefinition() == null ? parsed.getScenario() : parsed.getDefinition();
		if (selection == null) throw new IllegalArgumentException("A scenario or definition selection is required");
		repository.require(selection);
		try (RunnerSession session = new RunnerSession(engineFactory, repository)) {
			// Ctrl+C or a cancelled Gradle task must still stop the environment and its processes.
			Thread shutdown = new Thread(session::close, "anvil-runner-shutdown");
			Runtime.getRuntime().addShutdownHook(shutdown);
			try {
				new InteractiveSession(session, repository, selection, input, terminal).run();
			} finally {
				unregister(shutdown);
			}
		}
	}

	private static void unregister(@NotNull Thread shutdown) {
		try {
			Runtime.getRuntime().removeShutdownHook(shutdown);
		} catch (IllegalStateException ignored) {
			// The JVM is already running the hook, which closes the session.
		}
	}
}
