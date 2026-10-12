package me.whereareiam.anvil.integration.intellij.scenario.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.util.Objects;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.scenario.ChangeListeners;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Personal IDE behaviour of the scenario feature, persisted per user and independent of scenario runtime options.
 * <p>
 * Readers take one immutable {@link Values} snapshot; {@link #update(Values)} replaces it as a whole and notifies
 * subscribers once on the IDE event thread.
 */
@Service
@State(name = "AnvilScenarios", storages = @Storage("anvil-scenarios.xml"))
public final class ScenarioSettings implements PersistentStateComponent<ScenarioSettings.Options> {
	private static final int DEFAULT_COMPLETED_TABS = 5;

	private final ChangeListeners listeners = new ChangeListeners();
	private volatile @NotNull Values values = Values.builder().build();

	/**
	 * Returns the application's scenario settings.
	 */
	public static @NotNull ScenarioSettings getInstance() {
		return ApplicationManager.getApplication().getService(ScenarioSettings.class);
	}

	/**
	 * Returns the current settings.
	 */
	public @NotNull Values values() {
		return values;
	}

	/**
	 * Replaces every setting at once and notifies subscribers when anything changed.
	 */
	public void update(@NotNull Values next) {
		Values normalized = next.getCompletedTabs() < 0 ? next.toBuilder().completedTabs(DEFAULT_COMPLETED_TABS).build() : next;
		synchronized (this) {
			if (values.equals(normalized)) return;
			values = normalized;
		}

		var application = ApplicationManager.getApplication();
		if (application.isDispatchThread()) listeners.notifyNow();
		else application.invokeLater(listeners::notifyNow, ModalityState.any());
	}

	/**
	 * Calls the listener on the IDE event thread after each change, until the owner is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	@Override
	public @NotNull Options getState() {
		Options options = new Options();
		options.setExpandScenarios(values.isExpandScenarios());
		options.setRefreshAfterSync(values.isRefreshAfterSync());
		options.setOpeningSection(values.getOpeningSection());
		options.setRevealConsoleOnFailure(values.isRevealConsoleOnFailure());
		options.setCompletedTabs(values.getCompletedTabs());
		options.setCommandHistory(values.getCommandHistory());
		return options;
	}

	@Override
	public void loadState(@NotNull Options state) {
		update(Values.builder()
				.expandScenarios(state.isExpandScenarios())
				.refreshAfterSync(state.isRefreshAfterSync())
				.openingSection(Objects.requireNonNullElse(state.getOpeningSection(), Section.LAST_USED))
				.revealConsoleOnFailure(state.isRevealConsoleOnFailure())
				.completedTabs(state.getCompletedTabs())
				.commandHistory(Objects.requireNonNullElse(state.getCommandHistory(), History.SESSION))
				.build());
	}

	/**
	 * One complete, immutable set of scenario settings.
	 */
	@Value
	@Builder(toBuilder = true)
	public static class Values {
		/**
		 * Whether the catalog shows each scenario's servers and proxies without being expanded by hand.
		 */
		boolean expandScenarios;
		/**
		 * Whether a successful project sync reloads the selected source's scenarios.
		 */
		@Builder.Default
		boolean refreshAfterSync = true;
		/**
		 * Section a new environment tab opens on.
		 */
		@NotNull
		@Builder.Default
		Section openingSection = Section.LAST_USED;
		/**
		 * Whether a failed environment brings its console to the front.
		 */
		@Builder.Default
		boolean revealConsoleOnFailure = true;
		/**
		 * How many tabs of environments that ended successfully stay open; zero keeps all of them.
		 */
		@Builder.Default
		int completedTabs = DEFAULT_COMPLETED_TABS;
		/**
		 * How long accepted console commands are remembered.
		 */
		@NotNull
		@Builder.Default
		History commandHistory = History.SESSION;
	}

	/**
	 * Section an environment tab opens on.
	 */
	@RequiredArgsConstructor
	public enum Section {
		LAST_USED("Last used"),
		ENVIRONMENT("Environment"),
		CONSOLE("Console"),
		PLAYERS("Players");

		private final @NotNull String label;

		@Override
		public @NotNull String toString() {
			return label;
		}
	}

	/**
	 * How long accepted console commands are remembered.
	 */
	@RequiredArgsConstructor
	public enum History {
		SESSION("Current IDE session"),
		PROJECT("Across IDE restarts");

		private final @NotNull String label;

		@Override
		public @NotNull String toString() {
			return label;
		}
	}

	/**
	 * Persisted form of {@link Values}.
	 */
	@Getter
	@Setter
	@EqualsAndHashCode
	public static final class Options {
		private boolean expandScenarios;
		private boolean refreshAfterSync = true;
		private @Nullable Section openingSection = Section.LAST_USED;
		private boolean revealConsoleOnFailure = true;
		private int completedTabs = DEFAULT_COMPLETED_TABS;
		private @Nullable History commandHistory = History.SESSION;
	}
}
