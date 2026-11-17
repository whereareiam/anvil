package me.whereareiam.anvil.protocol.java.api.model.overlay;

import lombok.Value;

/**
 * Changes how full a boss bar is.
 */
@Value
public class BossEventUpdateProgress implements BossEventOperation {
	/**
	 * How full the bar is, from {@code 0} to {@code 1}.
	 */
	float progress;
}
