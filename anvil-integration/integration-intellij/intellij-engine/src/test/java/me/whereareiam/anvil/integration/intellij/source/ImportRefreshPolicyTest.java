package me.whereareiam.anvil.integration.intellij.source;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportRefreshPolicyTest {
	private final AtomicBoolean enabled = new AtomicBoolean(true);
	private final ImportRefreshPolicy policy = new ImportRefreshPolicy(enabled::get);

	@Test
	void successfulImportOfANewRevisionRefreshesTheLoadedSource() {
		policy.loading(source("app", 1));

		policy.importSucceeded();
		policy.detected(source("app", 2));

		assertTrue(policy.refreshDue(source("app", 2)));
		policy.loading(source("app", 2));
		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void importWithUnchangedRevisionDoesNotRefresh() {
		policy.loading(source("app", 1));

		policy.importSucceeded();
		policy.detected(source("app", 1));

		assertFalse(policy.refreshDue(source("app", 1)));
	}

	@Test
	void detectionWithoutACompletedImportDoesNotRefresh() {
		policy.loading(source("app", 1));

		policy.detected(source("app", 2));

		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void neverLoadedSourceIsNotRefreshedByAnImport() {
		policy.importSucceeded();
		policy.detected(source("app", 2));

		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void alreadyLoadedCatalogProvidesTheBaselineRevision() {
		policy.catalogLoaded(source("app", 1));
		policy.catalogLoaded(source("app", 5));

		policy.importSucceeded();
		policy.detected(source("app", 2));

		assertTrue(policy.refreshDue(source("app", 2)));
	}

	@Test
	void failedImportCancelsAPendingRefresh() {
		policy.loading(source("app", 1));
		policy.importSucceeded();
		policy.detected(source("app", 2));

		policy.importFailed();

		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void selectingAnotherSourceDropsThePendingRefresh() {
		policy.loading(source("app", 1));
		policy.importSucceeded();
		policy.detected(source("app", 2));

		policy.detected(source("other", 1));

		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void explicitSelectionDropsThePendingRefresh() {
		policy.loading(source("app", 1));
		policy.importSucceeded();
		policy.detected(source("app", 2));

		policy.selectionChanged();

		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void disabledRefreshAcceptsImportsSoReenablingDoesNotReplayThem() {
		policy.loading(source("app", 1));
		enabled.set(false);

		policy.importSucceeded();
		policy.detected(source("app", 2));
		assertFalse(policy.refreshDue(source("app", 2)));

		enabled.set(true);
		policy.importSucceeded();
		policy.detected(source("app", 2));
		assertFalse(policy.refreshDue(source("app", 2)));
	}

	@Test
	void disablingRefreshCancelsAPendingRefresh() {
		policy.loading(source("app", 1));
		policy.importSucceeded();
		policy.detected(source("app", 2));

		enabled.set(false);
		policy.preferencesChanged(source("app", 2));
		enabled.set(true);

		assertFalse(policy.refreshDue(source("app", 2)));
	}

	private static ScenarioSource source(String id, long revision) {
		return ScenarioSource.builder()
				.id(id)
				.displayName(id)
				.integrationId("gradle")
				.directory(Path.of(id))
				.importRevision(revision)
				.build();
	}
}
