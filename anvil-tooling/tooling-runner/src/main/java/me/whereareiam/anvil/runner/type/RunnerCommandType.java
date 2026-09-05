package me.whereareiam.anvil.runner.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Commands supported by an interactive foreground scenario session.
 */
@Getter
@RequiredArgsConstructor
public enum RunnerCommandType {
	QUIT("quit", "quit"),
	EXIT("exit", "exit"),
	STATUS("status", "status"),
	LIST("list", "list"),
	START("start", "start <scenario>"),
	RESTART("restart", "restart"),
	LOGS("logs", "logs <process> [lines]"),
	SEND("send", "send <process> <command>"),
	STOP("stop", "stop"),
	UNKNOWN("", "");

	private static final Map<String, RunnerCommandType> BY_NAME = byName();

	private final String name;
	private final String usage;

	/**
	 * Resolves a command token, returning {@link #UNKNOWN} for unsupported input.
	 *
	 * @param name command token
	 * @return matching command or {@link #UNKNOWN}
	 */
	public static @NotNull RunnerCommandType fromName(@NotNull String name) {
		return BY_NAME.getOrDefault(name, UNKNOWN);
	}

	/**
	 * Returns the help line displayed when an interactive session starts.
	 *
	 * @return command help
	 */
	public static @NotNull String help() {
		return "Commands: " + String.join(", ",
				STATUS.usage,
				LIST.usage,
				START.usage,
				RESTART.usage,
				LOGS.usage,
				SEND.usage,
				STOP.usage,
				QUIT.usage
		);
	}

	private static @NotNull Map<String, RunnerCommandType> byName() {
		Map<String, RunnerCommandType> result = new LinkedHashMap<>();
		for (RunnerCommandType command : values()) result.put(command.name, command);

		return Map.copyOf(result);
	}
}
