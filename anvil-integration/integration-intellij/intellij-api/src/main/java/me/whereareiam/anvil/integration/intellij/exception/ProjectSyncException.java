package me.whereareiam.anvil.integration.intellij.exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A failed project sync with a readable summary and retained diagnostic details.
 */
@Getter
public final class ProjectSyncException extends RuntimeException {
	private final @NotNull String details;

	/**
	 * Retains the original failure while providing presentation that does not rely on exception class
	 * names.
	 */
	public ProjectSyncException(
			@Nullable String summary,
			@NotNull String details,
			@Nullable Throwable cause
	) {
		super(summary(summary), cause);
		this.details = details;
	}

	/**
	 * Removes asynchronous completion wrappers while preserving an integration's diagnostic summary.
	 */
	public static @NotNull ProjectSyncException from(@NotNull Throwable failure) {
		Throwable current = failure;
		while ((current instanceof CompletionException
				|| current instanceof ExecutionException)
				&& current.getCause() != null) current = current.getCause();
		if (current instanceof ProjectSyncException detailed) return detailed;

		StringWriter details = new StringWriter();
		current.printStackTrace(new PrintWriter(details));
		return new ProjectSyncException(current.getMessage(), details.toString(), current);
	}

	private static @NotNull String summary(@Nullable String message) {
		String text = message == null
				? ""
				: message.strip()
					.lines()
					.findFirst()
					.orElse("");

		int separator = text.indexOf(':');
		String prefix = separator < 0 ? text : text.substring(0, separator);
		if (prefix.matches("(?:[A-Za-z_$][\\w$]*\\.)+[A-Za-z_$][\\w$]*")) {
			text = separator < 0 ? "" : text.substring(separator + 1).strip();
		}

		return text.isEmpty()
				? "The IDE could not finish syncing this project. View sync details for the reported"
							+ " problems."
				: text;
	}
}
