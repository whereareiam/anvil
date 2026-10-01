package me.whereareiam.anvil.tooling.api.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.jetbrains.annotations.NotNull;

/**
 * Lifecycle state reported for one live scenario process. Unknown wire values remain readable.
 */
public enum ProcessState {
	CREATED,
	STARTING,
	READY,
	STOPPING,
	STOPPED,
	FAILED,
	UNKNOWN;

	/**
	 * Maps a wire value while preserving forward compatibility with newer runtimes.
	 */
	@JsonCreator
	public static @NotNull ProcessState fromWireValue(String value) {
		if (value == null) return UNKNOWN;

		try {
			return valueOf(value);
		} catch (IllegalArgumentException unknown) {
			return UNKNOWN;
		}
	}
}
