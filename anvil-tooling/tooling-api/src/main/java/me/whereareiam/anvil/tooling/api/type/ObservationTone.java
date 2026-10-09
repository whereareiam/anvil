package me.whereareiam.anvil.tooling.api.type;

/**
 * Presentation severity of a contributed observation.
 */
public enum ObservationTone {
	/**
	 * Neutral informational value.
	 */
	INFO,
	/**
	 * Healthy or successfully completed state.
	 */
	SUCCESS,
	/**
	 * Degraded or temporarily unavailable state.
	 */
	WARNING,
	/**
	 * Failed observation or unhealthy state.
	 */
	ERROR
}
