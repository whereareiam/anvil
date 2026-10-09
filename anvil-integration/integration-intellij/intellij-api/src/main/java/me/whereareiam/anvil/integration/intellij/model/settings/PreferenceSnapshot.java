package me.whereareiam.anvil.integration.intellij.model.settings;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import org.jetbrains.annotations.NotNull;

/**
 * Complete immutable application preferences for the IntelliJ integration.
 */
@Value
@Builder(toBuilder = true)
public class PreferenceSnapshot {
	boolean expandScenarioGroups;
	@NotNull SessionSection defaultSessionSection;
	boolean showConsoleOnFailure;
	int completedSuccessfulTabs;
	@NotNull CommandHistoryPersistence commandHistoryPersistence;
	boolean refreshCatalogAfterSync;
	@NotNull String accountsDirectory;
}
