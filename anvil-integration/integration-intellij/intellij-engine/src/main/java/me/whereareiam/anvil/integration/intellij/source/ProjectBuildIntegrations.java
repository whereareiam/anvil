package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.ide.trustedProjects.TrustedProjectsListener;
import com.intellij.ide.trustedProjects.TrustedProjectsLocator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Discovers project tooling through installed build integrations and resolves saved selections.
 */
@RequiredArgsConstructor
public final class ProjectBuildIntegrations implements BuildIntegrations {
	/**
	 * Installed build integrations registered through the plugin's native extension point.
	 */
	public static final @NotNull ExtensionPointName<BuildIntegration> BUILD_INTEGRATIONS =
			ExtensionPointName.create("me.whereareiam.anvil.buildIntegration");

	private static final SourceSelection SELECTIONS = new SourceSelection();
	private final @NotNull Project project;
	private @Nullable CompletableFuture<Void> activeSync;

	/**
	 * Reports whether an installed integration can sync a linked project.
	 */
	public boolean canSync() {
		return BUILD_INTEGRATIONS.getExtensionList().stream()
				.anyMatch(integration -> integration.canSync(project));
	}

	/**
	 * Syncs linked sources through their native IDE integrations. Repeated requests share the
	 * in-progress operation. Completion means imported model data is available.
	 */
	public synchronized @NotNull CompletableFuture<Void> sync() {
		if (!TrustedProjects.isProjectTrusted(project)) {
			return CompletableFuture.failedFuture(new IllegalStateException(
					"Trust this project in IntelliJ " +
							"IDEA before loading Anvil scenarios.")
			);
		}

		if (activeSync != null && !activeSync.isDone()) return activeSync;
		List<BuildIntegration> integrations =
				BUILD_INTEGRATIONS.getExtensionList().stream()
						.filter(integration -> integration.canSync(project))
						.toList();

		if (integrations.isEmpty()) {
			return CompletableFuture.failedFuture(new IllegalStateException(
					"Open or import a supported project " +
							"before syncing Anvil.")
			);
		}

		List<CompletableFuture<Void>> operations = new ArrayList<>();
		for (BuildIntegration integration : integrations) {
			try {
				operations.add(integration.sync(project));
			} catch (RuntimeException failure) {
				operations.add(CompletableFuture.failedFuture(failure));
			}
		}

		activeSync = CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new));
		return activeSync;
	}

	/**
	 * Observes imported project changes on the IDE event thread until the owner is disposed.
	 */
	public void subscribe(@NotNull Consumer<ProjectChange> listener, @NotNull Disposable owner) {
		CheckedDisposable subscription = Disposer.newCheckedDisposable(owner, "Anvil project changes");
		Consumer<ProjectChange> changed =
				change -> {
					if (project.isDisposed() || subscription.isDisposed()) return;
					ApplicationManager.getApplication()
							.invokeLater(
									() -> {
										if (!project.isDisposed() && !subscription.isDisposed())
											listener.accept(change);
									},
									ModalityState.any());
				};
		for (BuildIntegration integration : BUILD_INTEGRATIONS.getExtensionList())
			integration.subscribe(project, changed, owner);
		ApplicationManager.getApplication()
				.getMessageBus()
				.connect(owner)
				.subscribe(
						TrustedProjectsListener.TOPIC,
						new TrustedProjectsListener() {
							@Override
							public void onProjectTrusted(@NotNull TrustedProjectsLocator.LocatedProject trusted) {
								if (project.equals(trusted.getProject()))
									changed.accept(ProjectChange.TRUST_CHANGED);
							}

							@Override
							public void onProjectUntrusted(
									@NotNull TrustedProjectsLocator.LocatedProject untrusted) {
								if (project.equals(untrusted.getProject()))
									changed.accept(ProjectChange.TRUST_CHANGED);
							}
						}
				);
	}

	/**
	 * Returns a fresh snapshot of imported scenario sources without running any project code.
	 */
	public @NotNull SourceListing discover() {
		var sources = new LinkedHashMap<String, ScenarioSource>();
		List<SourceListing> results = new ArrayList<>();
		for (BuildIntegration integration : BUILD_INTEGRATIONS.getExtensionList()) {
			SourceListing result = integration.discover(project);
			results.add(result);
			result.getSources().forEach(source -> sources.put(source.getId(), source));
		}

		var imports = results.stream()
				.map(SourceListing::getActiveSync)
				.filter(Objects::nonNull)
				.toList();
		CompletableFuture<Void> importing = imports.isEmpty()
				? null
				: CompletableFuture.allOf(imports.toArray(CompletableFuture[]::new));

		if (!sources.isEmpty()) {
			List<ScenarioSource> available =
					sources.values().stream()
							.sorted(Comparator.comparing(ScenarioSource::getDisplayName)
									.thenComparing(ScenarioSource::getId))
							.toList();
			String message =
					results.stream()
							.filter(result -> result.getStatus() == SourceListingStatus.NOT_IMPORTED)
							.map(SourceListing::getMessage)
							.findFirst()
							.orElse(available.size() == 1
									? "Anvil project detected."
									: "Choose an Anvil project."
							);

			return SourceListing.builder()
					.sources(available)
					.status(SourceListingStatus.READY)
					.message(message)
					.activeSync(importing)
					.build();
		}

		for (SourceListingStatus status : List.of(
				SourceListingStatus.NOT_IMPORTED,
				SourceListingStatus.NOT_CONFIGURED)
		) {
			for (SourceListing result : results)
				if (result.getStatus() == status)
					return result.toBuilder()
						.activeSync(importing)
						.build();
		}

		return SourceListing.builder()
				.status(SourceListingStatus.UNSUPPORTED)
				.message(
						"No supported project integration is available. Open or import an Anvil-enabled"
								+ " project.")
				.build();
	}

	/**
	 * Resolves an exact saved identifier. An empty identifier selects the sole available source; a
	 * missing saved identifier never silently selects another source.
	 *
	 * @throws IllegalStateException if discovery is unavailable or the selection is missing or
	 *                               ambiguous
	 */
	public @NotNull ScenarioSource resolve(@NotNull String id) {
		return SELECTIONS.resolve(discover(), id);
	}

	/**
	 * Plans preparation through the selected scenario source's build integration without executing it.
	 * The caller owns the returned manifest even when execution fails or is cancelled.
	 */
	public @NotNull ScenarioPreparation prepare(@NotNull ScenarioSource selected) throws IOException {
		if (!TrustedProjects.isProjectTrusted(project)) {
			throw new IOException("Trust this project in IntelliJ " +
					"IDEA before loading Anvil scenarios.");
		}

		ScenarioSource current = resolve(selected.getId());
		for (BuildIntegration integration : BUILD_INTEGRATIONS.getExtensionList())
			if (integration.getId().equals(current.getIntegrationId()))
				return integration.prepare(project, current);

		throw new IllegalStateException(
				"The selected project's build integration is no longer available.");
	}
}
