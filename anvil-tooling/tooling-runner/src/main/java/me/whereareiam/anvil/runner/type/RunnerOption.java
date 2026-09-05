package me.whereareiam.anvil.runner.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supported long options for the foreground runner entry point.
 */
@Getter
@RequiredArgsConstructor
public enum RunnerOption {
	PROVIDER("provider"),
	LIST("list"),
	SCENARIO("scenario"),
	GROUP("group");

	private static final Map<String, RunnerOption> BY_NAME = byName();

	private final String name;

	/**
	 * Resolves one command-line option name.
	 *
	 * @param name option name without the leading dashes
	 * @return matching option
	 * @throws IllegalArgumentException when the option is unknown
	 */
	public static @NotNull RunnerOption fromName(@NotNull String name) {
		RunnerOption option = BY_NAME.get(name);
		if (option == null) throw new IllegalArgumentException("Unknown option: --" + name);
		return option;
	}

	private static @NotNull Map<String, RunnerOption> byName() {
		Map<String, RunnerOption> result = new LinkedHashMap<>();
		for (RunnerOption option : values()) result.put(option.name, option);

		return Map.copyOf(result);
	}
}
