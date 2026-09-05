package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.scenario.AnvilScenarioProvider;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.launcher.config.EngineProperties;
import me.whereareiam.anvil.runner.command.RunnerArgumentsParser;
import me.whereareiam.anvil.runner.model.RunnerSelection;
import me.whereareiam.anvil.runner.model.command.RunnerArguments;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Reusable foreground command-line runner for listing and joining manual Anvil scenarios.
 *
 * <p>The runner is deliberately independent of Gradle. A Gradle task, another plugin, or a
 * standalone entry point can provide the runtime configuration and terminal streams.</p>
 */
public final class AnvilRunner {
	private final Reader input;
	private final RunnerTerminal terminal;

	/**
	 * Creates a runner backed by the supplied terminal streams.
	 *
	 * @param input command input
	 * @param output user-facing command output
	 */
	public AnvilRunner(@NotNull Reader input, @NotNull PrintWriter output) {
		this.input = Objects.requireNonNull(input, "input");
		this.terminal = new RunnerTerminal(Objects.requireNonNull(output, "output"));
	}

	/**
	 * Runs the CLI using the current process standard input and output.
	 *
	 * @param arguments command-line arguments
	 * @throws Exception when provider loading or scenario startup fails
	 */
	public static void main(@NotNull String[] arguments) throws Exception {
		new AnvilRunner(
				new InputStreamReader(System.in, StandardCharsets.UTF_8),
				new PrintWriter(System.out, true)
		).run(arguments);
	}

	/**
	 * Runs the scenario CLI using system-property configuration.
	 *
	 * @param arguments command-line arguments
	 * @throws Exception when provider loading or scenario startup fails
	 */
	public void run(@NotNull String[] arguments) throws Exception {
		run(arguments, EngineProperties.fromSystemProperties());
	}

	/**
	 * Runs the scenario CLI with explicit runtime configuration.
	 *
	 * @param arguments command-line arguments
	 * @param configuration engine and workspace configuration
	 * @throws Exception when provider loading or scenario startup fails
	 */
	public void run(@NotNull String[] arguments, @NotNull EngineOptions configuration) throws Exception {
		Objects.requireNonNull(configuration, "configuration");
		RunnerArguments parsed = RunnerArgumentsParser.parse(arguments);
		ScenarioRegistry registry = loadRegistry(parsed.getProvider());

		if (parsed.isList()) {
			terminal.showScenarios(registry);
			return;
		}

		RunnerSelection selection = select(parsed, registry);
		try (
				ScenarioEngine engine = AnvilLauncher.create(configuration);
				InteractiveSession session = new InteractiveSession(
						engine,
						registry,
						selection.getScenario(),
						selection.getGroup(),
						input,
						terminal
				)
		) {
			session.run();
		}
	}

	private RunnerSelection select(RunnerArguments arguments, ScenarioRegistry registry) {
		String scenarioName = arguments.getScenario();
		if (scenarioName != null) {
			return RunnerSelection.builder()
					.scenario(registry.requireScenario(scenarioName))
					.build();
		}

		String groupName = arguments.getGroup();
		ScenarioGroup group = requireGroup(registry, groupName);
		if (group.getScenarios().isEmpty()) throw new IllegalArgumentException("Scenario group is empty: " + group.getName());

		return RunnerSelection.builder()
				.scenario(registry.requireScenario(group.getScenarios().getFirst()))
				.group(group)
				.build();
	}

	private ScenarioRegistry loadRegistry(String providerName) throws ReflectiveOperationException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<? extends AnvilScenarioProvider> providerType = Class
				.forName(providerName, true, loader)
				.asSubclass(AnvilScenarioProvider.class);

		AnvilScenarioProvider provider = providerType.getDeclaredConstructor().newInstance();
		ScenarioRegistry registry = new ScenarioRegistry();
		provider.register(registry);

		return registry;
	}

	private ScenarioGroup requireGroup(ScenarioRegistry registry, String name) {
		return registry.groups().stream()
				.filter(group -> group.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown group: " + name));
	}
}
