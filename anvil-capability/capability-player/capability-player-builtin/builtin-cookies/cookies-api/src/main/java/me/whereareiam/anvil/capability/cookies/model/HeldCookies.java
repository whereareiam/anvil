package me.whereareiam.anvil.capability.cookies.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The cookies the player holds after a change, numbered so the host keeps the latest one whichever way it arrives:
 * as an event or as the answer to a change the test made.
 */
@Value
public class HeldCookies {
	/**
	 * Number of the change, increasing with each one.
	 */
	long version;
	/**
	 * Payloads by key, each key with its namespace.
	 */
	@NotNull Map<String, byte[]> cookies;
}
