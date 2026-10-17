package me.whereareiam.anvil.api.process;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sends commands to one running process and observes its captured console output.
 *
 * <p>Start a {@link ConsoleWatch} before an operation when the assertion must ignore earlier output.</p>
 */
public interface ProcessConsole {
	/**
	 * Sends a command without a leading slash.
	 *
	 * @param command command to send
	 */
	void sendCommand(@NotNull String command);

	/**
	 * Returns the most recent captured output lines.
	 *
	 * @param maximumLines maximum number of lines
	 * @return captured output tail
	 */
	@NotNull List<String> tail(int maximumLines);

	/**
	 * Starts following the lines the process prints from now on. Each watch keeps its own position.
	 *
	 * @return a watch that starts after the last line printed so far
	 */
	@NotNull ConsoleWatch watch();

	/**
	 * Starts following the process's output from the oldest line the bounded history still keeps, for example to
	 * replay everything a process printed.
	 *
	 * @return a watch that starts before the oldest kept line
	 */
	@NotNull ConsoleWatch watchFromStart();
}
