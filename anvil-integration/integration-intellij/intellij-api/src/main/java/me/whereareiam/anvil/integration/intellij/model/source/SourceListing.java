package me.whereareiam.anvil.integration.intellij.model.source;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Available scenario sources and the current native import state.
 */
@Value
@Builder(toBuilder = true)
public class SourceListing {
	@NotNull
	@Builder.Default
	List<ScenarioSource> sources = List.of();
	@NotNull SourceListingStatus status;
	@NotNull String message;
	@Nullable CompletableFuture<Void> activeSync;
}
