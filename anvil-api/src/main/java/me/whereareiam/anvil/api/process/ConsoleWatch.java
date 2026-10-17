package me.whereareiam.anvil.api.process;

import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Follows one process generation's console output from a position. Each wait or read continues after the line the
 * previous one ended at, so a line that repeats, such as a message logged on every join, is matched once per wait.
 * A restarted process has a new console; watch it again.
 *
 * <pre>{@code
 * ConsoleWatch output = server.console().watch();
 * server.console().sendCommand("save-all");
 * output.printed("Saved the game", Duration.ofSeconds(10));
 * output.neverPrinted("Exception", Duration.ofSeconds(2));
 * }</pre>
 */
public interface ConsoleWatch {
	/**
	 * Waits for the next line containing text. The following wait or read continues after it.
	 *
	 * @param text text the line must contain
	 * @param timeout maximum wait
	 * @return the matching line with its original formatting
	 * @throws IllegalArgumentException when the text is empty or the timeout is not positive
	 * @throws me.whereareiam.anvil.api.exception.ProcessException when no such line is printed in time, the process
	 *         has no further output, or lines after the watch's position were evicted from the bounded history
	 */
	default @NotNull String printed(@NotNull String text, @NotNull Duration timeout) {
		if (text.isEmpty()) throw new IllegalArgumentException("text must not be empty");

		return printed(line -> line.contains(text), timeout);
	}

	/**
	 * Waits for the next line that satisfies a condition. The following wait or read continues after it.
	 *
	 * @param matcher condition on the line with its original formatting
	 * @param timeout maximum wait
	 * @return the matching line
	 * @throws IllegalArgumentException when the timeout is not positive
	 * @throws me.whereareiam.anvil.api.exception.ProcessException when no such line is printed in time, the process
	 *         has no further output, or lines after the watch's position were evicted from the bounded history
	 */
	@NotNull String printed(@NotNull Predicate<String> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no line containing text is printed for a duration. It fails as soon as such a line has been
	 * printed since the previous wait, naming it, and returns normally once the duration has passed or the process has
	 * no further output. It does not move the watch.
	 *
	 * @param text text no line may contain
	 * @param duration how long no such line may be printed
	 * @throws me.whereareiam.anvil.api.exception.ProcessException when such a line is printed
	 */
	default void neverPrinted(@NotNull String text, @NotNull Duration duration) {
		if (text.isEmpty()) throw new IllegalArgumentException("text must not be empty");

		neverPrinted(line -> line.contains(text), duration);
	}

	/**
	 * Expects that no line satisfying a condition is printed for a duration. It fails as soon as such a line has been
	 * printed since the previous wait, naming it, and returns normally once the duration has passed or the process has
	 * no further output. It does not move the watch.
	 *
	 * @param matcher condition on the line with its original formatting
	 * @param duration how long no such line may be printed
	 * @throws me.whereareiam.anvil.api.exception.ProcessException when such a line is printed
	 */
	void neverPrinted(@NotNull Predicate<String> matcher, @NotNull Duration duration);

	/**
	 * Reads the lines printed since the previous wait or read without waiting, and moves the watch after the last
	 * line returned.
	 *
	 * @param maximumLines positive read limit
	 * @return the lines, and whether earlier unread lines were evicted or the process has no further output
	 */
	@NotNull ConsoleOutput read(int maximumLines);
}
