package me.whereareiam.anvil.tooling.api.model.process;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Selects a declared process for a lifecycle operation.
 */
@Value
@Builder
@Jacksonized
public class ProcessControlRequest {
	/**
	 * Stable process name in the current environment.
	 */
	@NotNull String target;
}
