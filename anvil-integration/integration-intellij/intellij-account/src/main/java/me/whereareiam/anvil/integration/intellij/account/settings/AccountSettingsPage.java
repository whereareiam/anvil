package me.whereareiam.anvil.integration.intellij.account.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings | Tools | Anvil | Accounts: where the global and this project's account files live.
 * <p>
 * The form owns layout and editable text; this page resolves paths, validates them, and stores them in
 * {@link AccountSettings} and the project's {@link AccountLibrary}. Changing a directory never moves files.
 */
public final class AccountSettingsPage implements SearchableConfigurable {
	/**
	 * Settings page identifier, for opening the page from other screens.
	 */
	public static final String ID = "me.whereareiam.anvil.accounts";

	private final @NotNull Project project;
	private @Nullable AccountSettingsForm form;

	public AccountSettingsPage(@NotNull Project project) {
		this.project = project;
	}

	@Override
	public @NotNull String getId() {
		return ID;
	}

	@Override
	public @NotNull String getDisplayName() {
		return "Accounts";
	}

	@Override
	public @NotNull JComponent createComponent() {
		if (form == null) form = new AccountSettingsForm(project);
		return form.getComponent();
	}

	@Override
	public boolean isModified() {
		if (form == null) return false;

		return !form.globalDirectory().equals(display(AccountSettings.getInstance().directory()))
				|| !form.projectDirectory().equals(display(library().directory()))
				|| form.includesGlobal() != library().includesGlobal();
	}

	@Override
	public void apply() throws ConfigurationException {
		if (form == null) return;

		Path global = resolve(form.globalDirectory(), "global");
		Path own = resolve(form.projectDirectory(), "project");
		AccountSettings.getInstance().setDirectory(global);
		library().setDirectory(own);
		library().setIncludesGlobal(form.includesGlobal());
		reset();
	}

	@Override
	public void reset() {
		if (form == null) return;

		form.reset(
				display(AccountSettings.getInstance().directory()),
				display(library().directory()),
				library().includesGlobal(),
				display(AccountSettings.getInstance().defaultDirectory()),
				display(library().defaultDirectory()));
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return form == null ? null : form.preferredFocus();
	}

	@Override
	public void disposeUIResources() {
		form = null;
	}

	private @NotNull AccountLibrary library() {
		return AccountLibrary.getInstance(project);
	}

	/**
	 * Resolves a directory typed by the user; relative paths are relative to the project directory.
	 */
	private @NotNull Path resolve(@NotNull String text, @NotNull String which) throws ConfigurationException {
		if (text.isBlank()) throw new ConfigurationException("Enter the " + which + " account directory.");

		Path path;
		try {
			path = Path.of(text.trim());
		} catch (InvalidPathException invalid) {
			throw new ConfigurationException("The " + which + " account directory is not a valid path: " + invalid.getMessage());
		}

		if (!path.isAbsolute()) {
			String base = project.getBasePath();
			if (base == null) throw new ConfigurationException("Use an absolute " + which + " account directory.");
			path = Path.of(base).resolve(path);
		}

		path = path.normalize();
		if (Files.exists(path) && !Files.isDirectory(path))
			throw new ConfigurationException("The " + which + " account directory is a file. Choose a directory.");

		return path;
	}

	/**
	 * Shows a directory relative to the project when it lies inside it.
	 */
	private @NotNull String display(@NotNull Path directory) {
		Path path = directory.toAbsolutePath().normalize();
		String base = project.getBasePath();
		if (base == null) return path.toString();

		Path root = Path.of(base).toAbsolutePath().normalize();
		if (!path.startsWith(root)) return path.toString();

		String relative = root.relativize(path).toString();
		return relative.isEmpty() ? "." : relative;
	}
}
