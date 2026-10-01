package me.whereareiam.anvil.integration.intellij.view.window.main.environment;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers this project's last manually selected run section in its local IDE workspace.
 */
@Service(Service.Level.PROJECT)
@State(name = "EnvironmentViewState", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class EnvironmentViewState implements PersistentStateComponent<EnvironmentViewState.Options> {
	private static final @NotNull SessionSection DEFAULT_SECTION = SessionSection.ENVIRONMENT;

	@Getter
	private @NotNull SessionSection lastSection = DEFAULT_SECTION;

	void rememberSection(@NotNull SessionSection section) {
		lastSection = section == SessionSection.LAST_USED ? DEFAULT_SECTION : section;
	}

	@Override
	public @NotNull Options getState() {
		Options options = new Options();
		options.setLastSection(lastSection);
		return options;
	}

	@Override
	public void loadState(@NotNull Options state) {
		SessionSection section = state.getLastSection();
		rememberSection(section == null ? DEFAULT_SECTION : section);
	}

	@Getter
	@Setter
	public static final class Options {
		private @Nullable SessionSection lastSection = DEFAULT_SECTION;
	}
}
