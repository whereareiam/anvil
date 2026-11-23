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
	LIST("list"),
	SCENARIO("scenario"),
	DEFINITION("definition");

	private static final Map<String, RunnerOption> BY_TOKEN = byToken();

	private final String token;

	/**
	 * Resolves one command-line option name.
	 *
	 * @param token option token without the leading dashes
	 * @return matching option
	 * @throws IllegalArgumentException when the option is unknown
	 */
	public static @NotNull RunnerOption fromToken(@NotNull String token) {
		RunnerOption option = BY_TOKEN.get(token);
		if (option == null) throw new IllegalArgumentException("Unknown option: --" + token);
		return option;
	}

	private static @NotNull Map<String, RunnerOption> byToken() {
		Map<String, RunnerOption> result = new LinkedHashMap<>();
		for (RunnerOption option : values()) result.put(option.token, option);

		return Map.copyOf(result);
	}
}
