package me.whereareiam.anvil.integration.intellij.model.settings;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Stable scenario source, scenario, target, and operation identity for accepted command history.
 */
@Value
@Builder(toBuilder = true)
public class CommandScope {
	@NotNull String source;
	@NotNull String definition;
	@NotNull String scenario;
	@NotNull String target;
	@NotNull String operation;
}
