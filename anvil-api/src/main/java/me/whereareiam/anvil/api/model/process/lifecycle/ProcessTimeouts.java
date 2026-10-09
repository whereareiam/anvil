package me.whereareiam.anvil.api.model.process.lifecycle;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Per-process startup and shutdown deadlines. Omitted members inherit independently
 * from engine defaults; configuring one timeout does not reset the other.
 */
@Value
@Builder(toBuilder = true)
public class ProcessTimeouts {
	/**
	 * Time allowed for one process to become ready, or null to inherit the default.
	 */
	@Nullable Duration startup;

	/**
	 * Grace period before escalating termination, or null to inherit the default.
	 */
	@Nullable Duration shutdown;

	/**
	 * Fills each omitted timeout from the supplied defaults without changing either value.
	 *
	 * @param defaults fallback startup and shutdown deadlines
	 * @return combined timeout selection
	 */
	public @NotNull ProcessTimeouts withDefaults(@NotNull ProcessTimeouts defaults) {
		return ProcessTimeouts.builder()
				.startup(startup == null ? defaults.startup : startup)
				.shutdown(shutdown == null ? defaults.shutdown : shutdown)
				.build();
	}
}
