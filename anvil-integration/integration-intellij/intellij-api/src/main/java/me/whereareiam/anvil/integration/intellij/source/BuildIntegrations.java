package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.openapi.Disposable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Discovers and prepares Anvil scenario sources imported into one IntelliJ project.
 * <p>
 * This project service combines every installed {@link BuildIntegration}. Features use it instead of
 * reaching an integration directly, so a project with several build systems behaves as one.
 */
public interface BuildIntegrations {
	/**
	 * Reports whether a linked project can be synchronized.
	 */
	boolean canSync();

	/**
	 * Syncs linked projects, sharing an existing in-progress operation.
	 */
	@NotNull CompletableFuture<Void> sync();

	/**
	 * Delivers import and trust changes on the IDE event thread until the owner is disposed.
	 */
	void subscribe(@NotNull Consumer<ProjectChange> listener, @NotNull Disposable owner);

	/**
	 * Returns the current imported source catalog without executing project code.
	 */
	@NotNull SourceListing discover();

	/**
	 * Resolves a saved source ID; an empty ID selects the sole available source.
	 *
	 * @throws IllegalStateException if no source is available, the ID is unknown, or an empty ID is ambiguous
	 */
	@NotNull ScenarioSource resolve(@NotNull String id);

	/**
	 * Finds the discovered source whose module directory contains a file, preferring the innermost module.
	 *
	 * @param file absolute path of a project file, such as a scenario definition's source
	 * @return the owning source, or null when no discovered source contains the file
	 */
	@Nullable ScenarioSource findSource(@NotNull Path file);

	/**
	 * Plans preparation without executing it. The caller owns the returned manifest location.
	 *
	 * @throws IOException if preparation cannot be planned
	 */
	@NotNull ScenarioPreparation prepare(@NotNull ScenarioSource source) throws IOException;
}
