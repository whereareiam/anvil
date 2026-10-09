package me.whereareiam.anvil.integration.intellij.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.nio.file.Path;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.anvil.integration.intellij.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.model.settings.PreferenceSnapshot;
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application preferences persisted by IntelliJ and published as immutable snapshots.
 */
@State(name = "AnvilSettings", storages = @Storage("anvil.xml"))
public final class PersistentPreferences
		implements Preferences, PersistentStateComponent<PersistentPreferences.PreferenceState> {
	private static final int DEFAULT_COMPLETED_SUCCESSFUL_TABS = 5;

	private final ChangeListeners listeners = new ChangeListeners();
	private volatile @NotNull PreferenceSnapshot values = defaults();

	/**
	 * Returns the application-scoped preferences service.
	 */
	public static @NotNull PersistentPreferences getInstance() {
		return (PersistentPreferences) ApplicationManager.getApplication().getService(Preferences.class);
	}

	/**
	 * Returns the configured global account directory or the user-local default.
	 */
	@Override
	public @NotNull Path accountsDirectory() {
		String configured = values.getAccountsDirectory();
		if (!configured.isBlank()) return Path.of(configured);
		return Path.of(System.getProperty("user.home"), ".anvil", "accounts");
	}

	@Override
	public @NotNull PreferenceSnapshot snapshot() {
		return values;
	}

	public boolean isExpandScenarioGroups() {
		return values.isExpandScenarioGroups();
	}

	public void setExpandScenarioGroups(boolean expanded) {
		update(values.toBuilder().expandScenarioGroups(expanded).build());
	}

	public @NotNull SessionSection getDefaultSessionSection() {
		return values.getDefaultSessionSection();
	}

	public void setDefaultSessionSection(@NotNull SessionSection section) {
		update(values.toBuilder().defaultSessionSection(section).build());
	}

	public boolean isShowConsoleOnFailure() {
		return values.isShowConsoleOnFailure();
	}

	public void setShowConsoleOnFailure(boolean show) {
		update(values.toBuilder().showConsoleOnFailure(show).build());
	}

	public int getCompletedSuccessfulTabs() {
		return values.getCompletedSuccessfulTabs();
	}

	public void setCompletedSuccessfulTabs(int count) {
		update(values.toBuilder().completedSuccessfulTabs(count).build());
	}

	public @NotNull CommandHistoryPersistence getCommandHistoryPersistence() {
		return values.getCommandHistoryPersistence();
	}

	public void setCommandHistoryPersistence(@NotNull CommandHistoryPersistence persistence) {
		update(values.toBuilder().commandHistoryPersistence(persistence).build());
	}

	public boolean isRefreshCatalogAfterSync() {
		return values.isRefreshCatalogAfterSync();
	}

	public void setRefreshCatalogAfterSync(boolean refresh) {
		update(values.toBuilder().refreshCatalogAfterSync(refresh).build());
	}

	public void setAccountsDirectory(@NotNull Path directory) {
		update(
				values.toBuilder()
						.accountsDirectory(directory.toAbsolutePath().normalize().toString())
						.build());
	}

	@Override
	public void update(@NotNull PreferenceSnapshot preferences) {
		replace(normalize(preferences));
	}

	@Override
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	@Override
	public @NotNull PreferenceState getState() {
		return PreferenceState.from(values);
	}

	@Override
	public void loadState(@NotNull PreferenceState state) {
		replace(PreferenceState.toSnapshot(state));
	}

	private void replace(@NotNull PreferenceSnapshot next) {
		synchronized (this) {
			if (values.equals(next)) return;
			values = next;
		}
		notifyListeners();
	}

	private void notifyListeners() {
		var application = ApplicationManager.getApplication();
		Runnable changed =
				() -> {
					if (!application.isDisposed()) listeners.notifyNow();
				};
		if (application.isDisposed()) return;
		if (application.isDispatchThread()) changed.run();
		else application.invokeLater(changed, ModalityState.any());
	}

	private static @NotNull PreferenceSnapshot defaults() {
		return PreferenceSnapshot.builder()
				.expandScenarioGroups(false)
				.defaultSessionSection(SessionSection.LAST_USED)
				.showConsoleOnFailure(true)
				.completedSuccessfulTabs(DEFAULT_COMPLETED_SUCCESSFUL_TABS)
				.commandHistoryPersistence(CommandHistoryPersistence.SESSION)
				.refreshCatalogAfterSync(true)
				.accountsDirectory("")
				.build();
	}

	private static @NotNull PreferenceSnapshot normalize(@NotNull PreferenceSnapshot preferences) {
		return preferences.toBuilder()
				.defaultSessionSection(preferences.getDefaultSessionSection())
				.completedSuccessfulTabs(preferences.getCompletedSuccessfulTabs() < 0
						? DEFAULT_COMPLETED_SUCCESSFUL_TABS
						: preferences.getCompletedSuccessfulTabs())
				.commandHistoryPersistence(preferences.getCommandHistoryPersistence())
				.accountsDirectory(preferences.getAccountsDirectory())
				.build();
	}

	/**
	 * IntelliJ XML state for the preference snapshot.
	 */
	@Getter
	@Setter
	@EqualsAndHashCode
	@NoArgsConstructor
	@AllArgsConstructor
	public static final class PreferenceState {
		private boolean expandScenarioGroups;
		private @Nullable SessionSection defaultSessionSection = SessionSection.LAST_USED;
		private boolean showConsoleOnFailure = true;
		private int completedSuccessfulTabs = DEFAULT_COMPLETED_SUCCESSFUL_TABS;
		private @Nullable CommandHistoryPersistence commandHistoryPersistence =
				CommandHistoryPersistence.SESSION;
		private boolean refreshCatalogAfterSync = true;
		private @Nullable String accountsDirectory = "";

		private static @NotNull PreferenceState from(@NotNull PreferenceSnapshot preferences) {
			return new PreferenceState(
					preferences.isExpandScenarioGroups(),
					preferences.getDefaultSessionSection(),
					preferences.isShowConsoleOnFailure(),
					preferences.getCompletedSuccessfulTabs(),
					preferences.getCommandHistoryPersistence(),
					preferences.isRefreshCatalogAfterSync(),
					preferences.getAccountsDirectory());
		}

		private static @NotNull PreferenceSnapshot toSnapshot(@NotNull PreferenceState state) {
			return normalize(
					PreferenceSnapshot.builder()
							.expandScenarioGroups(state.isExpandScenarioGroups())
							.defaultSessionSection(
									Objects.requireNonNullElse(
											state.getDefaultSessionSection(), SessionSection.LAST_USED))
							.showConsoleOnFailure(state.isShowConsoleOnFailure())
							.completedSuccessfulTabs(state.getCompletedSuccessfulTabs())
							.commandHistoryPersistence(
									Objects.requireNonNullElse(
											state.getCommandHistoryPersistence(),
											CommandHistoryPersistence.SESSION))
							.refreshCatalogAfterSync(state.isRefreshCatalogAfterSync())
							.accountsDirectory(state.getAccountsDirectory())
							.build());
		}
	}
}
