package me.whereareiam.anvil.tooling.api.model.action.observation;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.type.ObservationTone;
import org.jetbrains.annotations.NotNull;

/**
 * Rendered scalar observation with presentation severity.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ObservationValue {
	/**
	 * Current observation text.
	 */
	@NotNull
	String text;

	/**
	 * Severity used when presenting the value.
	 */
	@NotNull
	@Builder.Default
	ObservationTone tone = ObservationTone.INFO;
}
