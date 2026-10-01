package me.whereareiam.anvil.runner.command;

import me.whereareiam.anvil.runner.model.command.RunnerArguments;
import me.whereareiam.anvil.runner.type.RunnerOption;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Parses and validates startup arguments for the foreground runner.
 */
public final class RunnerArgumentsParser {
	/**
	 * Parses command-line options into an immutable model.
	 *
	 * @param arguments raw command-line arguments
	 * @return parsed runner arguments
	 */
	public static @NotNull RunnerArguments parse(@NotNull String[] arguments) {
		Map<RunnerOption, String> options = new EnumMap<>(RunnerOption.class);
		for (String argument : arguments) {
			if (!argument.startsWith("--")) throw new IllegalArgumentException("Unexpected argument: " + argument);

			int equals = argument.indexOf('=');
			String name = argument.substring(2, equals < 0 ? argument.length() : equals);
			RunnerOption option = RunnerOption.fromName(name);
			String value = equals < 0 ? "true" : argument.substring(equals + 1);
			if (options.putIfAbsent(option, value) != null) throw new IllegalArgumentException("Duplicate option: --" + name);
		}

		String scenario = optional(options, RunnerOption.SCENARIO);
		String definition = optional(options, RunnerOption.DEFINITION);
		boolean list = options.containsKey(RunnerOption.LIST);

		if (list && (scenario != null || definition != null)) {
			throw new IllegalArgumentException("Select --list or exactly one of --scenario=<name> or --definition=<class>");
		}
		if (!list && (scenario == null) == (definition == null)) {
			throw new IllegalArgumentException("Select exactly one of --scenario=<name> or --definition=<class>");
		}

		return RunnerArguments.builder()
				.list(list)
				.scenario(scenario)
				.definition(definition)
				.build();
	}

	private static String optional(Map<RunnerOption, String> options, RunnerOption option) {
		String value = options.get(option);
		if (value != null && value.isBlank()) {
			throw new IllegalArgumentException("--" + option.getName() + "=<value> must not be blank");
		}

		return value;
	}
}
