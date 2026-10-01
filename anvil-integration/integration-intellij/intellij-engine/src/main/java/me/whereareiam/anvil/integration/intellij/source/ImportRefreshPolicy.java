package me.whereareiam.anvil.integration.intellij.source;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decides when a successful project import reloads the selected source's scenarios.
 * <p>
 * The policy remembers the import revision at which each source was last loaded or accepted. After an
 * import succeeds, the next detection marks the selected source for a refresh when its revision changed.
 * While automatic refresh is disabled, detections accept the current revision instead, so enabling it later
 * does not replay earlier imports. All methods run on the IDE event thread.
 */
final class ImportRefreshPolicy {
	private final @NotNull BooleanSupplier enabled;
	private final @NotNull Map<String, Long> revisions = new HashMap<>();
	private @Nullable ScenarioSource pending;
	private boolean inspectNextDetection;

	/**
	 * Creates a policy that reads the automatic-refresh preference whenever it decides.
	 *
	 * @param enabled reports the current automatic-refresh preference
	 */
	ImportRefreshPolicy(@NotNull BooleanSupplier enabled) {
		this.enabled = enabled;
	}

	/**
	 * Records a successful import; the next detection compares the selected source's revision.
	 */
	void importSucceeded() {
		inspectNextDetection |= enabled.getAsBoolean();
	}

	/**
	 * Records a failed import or sync; nothing refreshes until a later import succeeds.
	 */
	void importFailed() {
		inspectNextDetection = false;
		pending = null;
	}

	/**
	 * Records the revision of a catalog that is already loaded, unless the source's revision is known.
	 */
	void catalogLoaded(@NotNull ScenarioSource source) {
		revisions.putIfAbsent(source.getId(), source.getImportRevision());
	}

	/**
	 * Applies a completed detection for the source it selected.
	 */
	void detected(@Nullable ScenarioSource selected) {
		if (pending != null && (selected == null || !pending.getId().equals(selected.getId()))) pending = null;
		if (selected != null && !enabled.getAsBoolean()) accept(selected);
		if (inspectNextDetection && selected != null && enabled.getAsBoolean()) {
			Long previous = revisions.get(selected.getId());
			if (previous != null && previous != selected.getImportRevision()) pending = selected;
		}

		inspectNextDetection = false;
	}

	/**
	 * Accepts the selected source's current revision when automatic refresh has been disabled.
	 */
	void preferencesChanged(@Nullable ScenarioSource selected) {
		if (enabled.getAsBoolean()) return;

		pending = null;
		inspectNextDetection = false;
		if (selected != null) accept(selected);
	}

	/**
	 * Drops a pending refresh after the user selects a source explicitly.
	 */
	void selectionChanged() {
		pending = null;
	}

	/**
	 * Reports whether an import requires the selected source's scenarios to be reloaded now.
	 */
	boolean refreshDue(@NotNull ScenarioSource selected) {
		return enabled.getAsBoolean()
				&& pending != null
				&& pending.getId().equals(selected.getId())
				&& pending.getImportRevision() == selected.getImportRevision();
	}

	/**
	 * Records that the source's scenarios are loading at its current revision.
	 */
	void loading(@NotNull ScenarioSource source) {
		pending = null;
		accept(source);
	}

	private void accept(@NotNull ScenarioSource source) {
		revisions.put(source.getId(), source.getImportRevision());
	}
}
