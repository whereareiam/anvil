package me.whereareiam.anvil.capability.worlds.agent;

import org.jetbrains.annotations.NotNull;

/**
 * Compares Minecraft versions such as {@code 1.21.11} and {@code 26.1.2} by their numbers, for the platform side of a
 * release range to tell whether it answers on the server it runs on.
 */
public final class MinecraftRelease {
	private MinecraftRelease() {
	}

	/**
	 * Tells whether a server's Minecraft version lies in a range of releases.
	 *
	 * @param version the server's Minecraft version, such as {@code 1.21.4}
	 * @param from the first release of the range
	 * @param until the first release after the range
	 * @return whether {@code from <= version < until}
	 * @throws IllegalArgumentException when a version is not dot-separated numbers
	 */
	public static boolean within(@NotNull String version, @NotNull String from, @NotNull String until) {
		return compare(version, from) >= 0 && compare(version, until) < 0;
	}

	/**
	 * Tells whether a server's Minecraft version is a release or later.
	 *
	 * @param version the server's Minecraft version
	 * @param from the first release
	 * @return whether {@code from <= version}
	 * @throws IllegalArgumentException when a version is not dot-separated numbers
	 */
	public static boolean since(@NotNull String version, @NotNull String from) {
		return compare(version, from) >= 0;
	}

	private static int compare(String left, String right) {
		String[] first = left.split("\\.");
		String[] second = right.split("\\.");
		for (int index = 0; index < Math.max(first.length, second.length); index++) {
			int difference = Integer.compare(number(first, index), number(second, index));
			if (difference != 0) return difference;
		}

		return 0;
	}

	private static int number(String[] parts, int index) {
		if (index >= parts.length) return 0;

		try {
			return Integer.parseInt(parts[index]);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Not a Minecraft release: " + String.join(".", parts), exception);
		}
	}
}
