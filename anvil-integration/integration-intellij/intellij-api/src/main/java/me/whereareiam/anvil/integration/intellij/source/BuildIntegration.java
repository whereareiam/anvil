package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import org.jetbrains.annotations.NotNull;

/**
 * Connects a native IntelliJ build integration to scenario-source discovery and preparation.
 */
public interface BuildIntegration {
	/**
	 * Returns the stable integration identifier stored with source selections.
	 */
	@NotNull String getId();

	/**
	 * Reads imported source information without executing project code.
	 */
	@NotNull SourceListing discover(@NotNull Project project);

	/**
	 * Reports whether this integration can sync a linked project.
	 */
	boolean canSync(@NotNull Project project);

	/**
	 * Syncs linked projects and completes after imported model data is available.
	 */
	@NotNull CompletableFuture<Void> sync(@NotNull Project project);

	/**
	 * Observes native import outcomes until the owner is disposed.
	 */
	void subscribe(
			@NotNull Project project,
			@NotNull Consumer<ProjectChange> listener,
			@NotNull Disposable owner
	);

	/**
	 * Resolves preparation instructions for the selected source without executing them.
	 *
	 * @throws IOException if preparation cannot be planned
	 */
	@NotNull ScenarioPreparation prepare(
			@NotNull Project project,
			@NotNull ScenarioSource source
	) throws IOException;
}
