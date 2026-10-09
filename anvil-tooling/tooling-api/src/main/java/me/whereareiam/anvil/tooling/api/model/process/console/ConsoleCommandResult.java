package me.whereareiam.anvil.tooling.api.model.process.console;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Reports console submission by the runner; acceptance does not confirm in-game execution.
 */
@Value
@Builder
@Jacksonized
public class ConsoleCommandResult {
	/**
	 * Whether the runner accepted the console command; required in every acknowledgement.
	 */
	@NotNull Boolean accepted;
}
