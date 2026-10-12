package me.whereareiam.anvil.integration.intellij.account.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.nio.file.Path;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application settings of the account feature: the global account directory every project may include.
 */
@Service
@State(name = "AnvilAccounts", storages = @Storage("anvil-accounts.xml"))
public final class AccountSettings implements PersistentStateComponent<AccountSettings.Options> {
	private @NotNull String directory = "";

	/**
	 * Returns the application's account settings.
	 */
	public static @NotNull AccountSettings getInstance() {
		return ApplicationManager.getApplication().getService(AccountSettings.class);
	}

	/**
	 * Returns the configured global account directory, or {@link #defaultDirectory()} when none is set.
	 */
	public @NotNull Path directory() {
		return directory.isBlank() ? defaultDirectory() : Path.of(directory);
	}

	/**
	 * Returns the global account directory used when none is configured: {@code ~/.anvil/accounts}, the engine's
	 * default account directory.
	 */
	public @NotNull Path defaultDirectory() {
		return Path.of(System.getProperty("user.home"), ".anvil", "accounts");
	}

	/**
	 * Stores an absolute normalized global directory; storing the default clears the override.
	 */
	public void setDirectory(@NotNull Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		directory = normalized.equals(defaultDirectory().toAbsolutePath().normalize()) ? "" : normalized.toString();
	}

	@Override
	public @NotNull Options getState() {
		Options options = new Options();
		options.setDirectory(directory);
		return options;
	}

	@Override
	public void loadState(@NotNull Options state) {
		directory = state.getDirectory() == null ? "" : state.getDirectory();
	}

	/**
	 * Persisted form: the global directory, blank for the default.
	 */
	@Getter
	@Setter
	public static final class Options {
		private @Nullable String directory = "";
	}
}
