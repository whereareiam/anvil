package me.whereareiam.anvil.integration.intellij.view.window.account;

import com.intellij.openapi.project.Project;

import java.nio.file.Files;
import java.nio.file.Path;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.view.window.account.component.AccountStoragePanel;
import org.jetbrains.annotations.NotNull;

final class AccountStorageController {
	private final @NotNull Project project;
	private final @NotNull AccountLibrary library;
	private final @NotNull Runnable changed;
	private final @NotNull AccountStoragePanel panel;

	AccountStorageController(
			@NotNull Project project,
			@NotNull AccountLibrary library,
			@NotNull Runnable changed
	) {
		this.project = project;
		this.library = library;
		this.changed = changed;
		panel = new AccountStoragePanel(
				project,
				displayPath(project, library.directory()),
				this::apply,
				() -> displayPath(project, library.defaultDirectory())
		);
	}

	@NotNull AccountStoragePanel panel() {
		return panel;
	}

	private void apply(@NotNull String text) {
		try {
			Path path = resolve(project, text);
			if (Files.exists(path) && !Files.isDirectory(path)) {
				throw new IllegalArgumentException("Choose a directory, not a file.");
			}

			library.setDirectory(path);
			panel.showApplied(displayPath(project, library.directory()));
			changed.run();
		} catch (RuntimeException failure) {
			panel.showFailure(failure.getMessage());
		}
	}

	private static @NotNull Path resolve(@NotNull Project project, @NotNull String text) {
		if (text.isBlank()) throw new IllegalArgumentException("Enter an account directory.");

		Path path = Path.of(text.trim());
		if (path.isAbsolute()) return path.normalize();
		if (project.getBasePath() == null) {
			throw new IllegalArgumentException("Use an absolute path for this project.");
		}

		return Path.of(project.getBasePath()).resolve(path).normalize();
	}

	private static @NotNull String displayPath(@NotNull Project project, @NotNull Path directory) {
		Path path = directory.toAbsolutePath().normalize();
		if (project.getBasePath() == null) return path.toString();

		Path root = Path.of(project.getBasePath()).toAbsolutePath().normalize();
		if (!path.startsWith(root)) return path.toString();

		String relative = root.relativize(path).toString();
		return relative.isEmpty() ? "." : relative;
	}
}
