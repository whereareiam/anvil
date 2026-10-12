package me.whereareiam.anvil.integration.intellij.scenario.console;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Severity of one console line, read from the log level that server and proxy loggers print.
 * <p>
 * Paper, Spigot, NeoForge, Velocity, and BungeeCord print a bracketed level near the start of each line, such as
 * {@code [12:00:00 WARN]:} or {@code [Server thread/ERROR]}. A line without a level, such as a stack frame,
 * continues the previous line of the same output channel.
 */
@Getter
@RequiredArgsConstructor
public enum ConsoleLevel {
	/**
	 * Ordinary output, including debug and trace lines.
	 */
	INFO("All output"),
	/**
	 * Warnings and anything more severe.
	 */
	WARNING("Warnings and errors"),
	/**
	 * Errors only.
	 */
	ERROR("Errors only");

	private static final Pattern LEVEL = Pattern.compile(
			"^\\s*(?:\\[[^\\]]*\\]\\s*)*?\\[(?:[^\\]]*[\\s/])?(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|SEVERE|FATAL)\\]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ESCAPE = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

	/**
	 * Describes the lines a filter at this level shows.
	 */
	private final @NotNull String label;

	/**
	 * Reads the level a line declares, or null when it declares none.
	 *
	 * @param text one line of output, possibly with ANSI escape sequences
	 */
	public static @Nullable ConsoleLevel declared(@NotNull String text) {
		Matcher matcher = LEVEL.matcher(ESCAPE.matcher(text).replaceAll(""));
		if (!matcher.find()) return null;

		return switch (matcher.group(1).toUpperCase(Locale.ROOT)) {
			case "WARN", "WARNING" -> WARNING;
			case "ERROR", "SEVERE", "FATAL" -> ERROR;
			default -> INFO;
		};
	}

	/**
	 * Classifies a line: its declared level, else the level of the line it continues, else error for standard
	 * error and info for standard output.
	 *
	 * @param previous level of the channel's previous line, or null for its first line
	 * @param error whether the line was written to standard error
	 */
	public static @NotNull ConsoleLevel of(@NotNull String text, @Nullable ConsoleLevel previous, boolean error) {
		ConsoleLevel declared = declared(text);
		if (declared != null) return declared;
		if (previous != null && continues(text)) return previous;

		return error ? ERROR : INFO;
	}

	/**
	 * Reports whether a line at this level passes a filter set to the given minimum.
	 */
	public boolean shownAt(@NotNull ConsoleLevel minimum) {
		return ordinal() >= minimum.ordinal();
	}

	private static boolean continues(@NotNull String text) {
		String plain = ESCAPE.matcher(text).replaceAll("");
		return plain.isBlank() || Character.isWhitespace(plain.charAt(0)) || plain.startsWith("Caused by:")
				|| plain.startsWith("...") || !plain.startsWith("[");
	}

	@Override
	public @NotNull String toString() {
		return label;
	}
}
