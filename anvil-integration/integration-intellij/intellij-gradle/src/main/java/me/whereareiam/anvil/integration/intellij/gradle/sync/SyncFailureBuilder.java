package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.build.events.MessageEvent;

import java.util.ArrayList;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.exception.ProjectSyncException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SyncFailureBuilder {
	private static final int MAX_PROBLEMS = 32;
	private static final int MAX_DETAIL_LENGTH = 16_384;
	private static final int MAX_MESSAGE_LENGTH = 2_048;

	private final List<Problem> problems = new ArrayList<>();

	synchronized void add(@NotNull MessageEvent event) {
		if (event.getKind() != MessageEvent.Kind.ERROR
				&& event.getKind() != MessageEvent.Kind.WARNING)
			return;

		Problem problem = problem(event);
		if (problems.contains(problem)) return;
		if (!retain(problem)) return;

		problems.add(problem);
	}

	synchronized @NotNull ProjectSyncException build(
			@NotNull String projectPath,
			@Nullable String message,
			@Nullable String nativeDetails,
			@Nullable Throwable cause
	) {
		StringBuilder details = details(projectPath, message, nativeDetails, cause);
		return new ProjectSyncException(summary(message), details.toString(), cause);
	}

	private @NotNull Problem problem(@NotNull MessageEvent event) {
		String message = event.getMessage();
		if (message.length() > MAX_MESSAGE_LENGTH) {
			message = message.substring(0, MAX_MESSAGE_LENGTH) + "…";
		}

		String details = event.getResult().getDetails();
		return new Problem(event.getKind(), message, details == null ? "" : limit(details));
	}

	private boolean retain(@NotNull Problem problem) {
		if (problems.size() < MAX_PROBLEMS) return true;
		if (problem.kind != MessageEvent.Kind.ERROR) return false;

		var warning = problems.stream()
				.filter(existing -> existing.kind == MessageEvent.Kind.WARNING)
				.findFirst();
		if (warning.isEmpty()) return false;

		problems.remove(warning.get());
		return true;
	}

	private @Nullable String summary(@Nullable String fallback) {
		return problems.stream()
				.filter(problem -> problem.kind == MessageEvent.Kind.ERROR)
				.map(Problem::message)
				.findFirst()
				.orElse(fallback);
	}

	private @NotNull StringBuilder details(
			@NotNull String projectPath,
			@Nullable String message,
			@Nullable String nativeDetails,
			@Nullable Throwable cause
	) {
		StringBuilder details = new StringBuilder("Project: ").append(projectPath).append("\n\n");
		if (message != null && !message.isBlank()) details.append(message.strip()).append("\n\n");

		for (Problem problem : problems)
			append(details, problem);

		if (nativeDetails != null && !nativeDetails.isBlank()) details.append(limit(nativeDetails)).append('\n');
		if (cause != null) details.append(ProjectSyncException.from(cause).getDetails());
		if (problems.isEmpty()) {
			details.append("Open the IDE Build tool window and select " +
					"Sync for the full project sync output.\n");
		}

		return details;
	}

	private void append(@NotNull StringBuilder details, @NotNull Problem problem) {
		details.append(problem.kind).append(": ").append(problem.message).append('\n');
		if (!problem.details.isBlank()) details.append(problem.details).append('\n');

		details.append('\n');
	}

	private static @NotNull String limit(@NotNull String text) {
		return text.length() <= MAX_DETAIL_LENGTH
				? text
				: text.substring(0, MAX_DETAIL_LENGTH)
						+ "\n[Remaining details are available in the IDE Build output.]";
	}

	private record Problem(MessageEvent.Kind kind, String message, String details) {}
}
