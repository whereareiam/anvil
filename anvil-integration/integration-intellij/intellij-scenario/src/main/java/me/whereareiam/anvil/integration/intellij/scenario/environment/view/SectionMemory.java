package me.whereareiam.anvil.integration.intellij.scenario.environment.view;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers the environment-tab section last chosen by hand in this project, in its private workspace file.
 */
@Service(Service.Level.PROJECT)
@State(name = "AnvilEnvironmentSection", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SectionMemory implements PersistentStateComponent<SectionMemory.Options> {
	private static final @NotNull ScenarioSettings.Section DEFAULT_SECTION = ScenarioSettings.Section.ENVIRONMENT;

	@Getter
	private @NotNull ScenarioSettings.Section lastSection = DEFAULT_SECTION;

	void rememberSection(@NotNull ScenarioSettings.Section section) {
		lastSection = section == ScenarioSettings.Section.LAST_USED ? DEFAULT_SECTION : section;
	}

	@Override
	public @NotNull Options getState() {
		Options options = new Options();
		options.setLastSection(lastSection);
		return options;
	}

	@Override
	public void loadState(@NotNull Options state) {
		ScenarioSettings.Section section = state.getLastSection();
		rememberSection(section == null ? DEFAULT_SECTION : section);
	}

	@Getter
	@Setter
	public static final class Options {
		private @Nullable ScenarioSettings.Section lastSection = DEFAULT_SECTION;
	}
}
