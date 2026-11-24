package me.whereareiam.anvil.testkit.physics.harness;

/**
 * Compares Minecraft versions such as {@code 1.21.11} and {@code 26.3} part by part.
 */
public final class MinecraftVersions {
	private MinecraftVersions() {
	}

	/**
	 * Compares two versions.
	 *
	 * @param left  one version
	 * @param right another version
	 * @return a negative number, zero or a positive number as {@code left} is older than, the same as or newer than
	 * {@code right}
	 */
	public static int compare(String left, String right) {
		String[] leftParts = left.split("\\.");
		String[] rightParts = right.split("\\.");
		for (int part = 0; part < Math.max(leftParts.length, rightParts.length); part++) {
			int difference = Integer.compare(part(leftParts, part), part(rightParts, part));
			if (difference != 0)
				return difference;
		}
		return 0;
	}

	private static int part(String[] parts, int index) {
		return index < parts.length ? Integer.parseInt(parts[index]) : 0;
	}
}
