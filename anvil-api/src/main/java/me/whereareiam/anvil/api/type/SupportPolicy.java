package me.whereareiam.anvil.api.type;

import org.jetbrains.annotations.NotNull;

/**
 * Decides which {@link SupportLevel support levels} a scenario may run with. Every policy refuses
 * {@link SupportLevel#UNSUPPORTED} and reports {@link SupportLevel#COMPATIBLE} as information.
 */
public enum SupportPolicy {
	/**
	 * Runs {@link SupportLevel#UNTESTED} components with a warning. This is the default, so a new
	 * Minecraft release keeps working without code changes when nothing it relies on broke.
	 */
	LENIENT,
	/**
	 * Refuses {@link SupportLevel#UNTESTED} components, for example in release verification.
	 */
	STRICT;

	/**
	 * Tests whether a component with the given level may run under this policy.
	 *
	 * @param level assessed support level
	 * @return whether the component may run
	 */
	public boolean permits(@NotNull SupportLevel level) {
		return switch (level) {
			case VERIFIED, COMPATIBLE -> true;
			case UNTESTED -> this == LENIENT;
			case UNSUPPORTED -> false;
		};
	}
}
