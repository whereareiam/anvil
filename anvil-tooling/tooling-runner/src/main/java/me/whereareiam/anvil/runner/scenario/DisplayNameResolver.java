package me.whereareiam.anvil.runner.scenario;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the label tooling shows for a scenario, process or player: its presentation metadata's display name
 * when one is set, else a readable form of its technical identity.
 */
public final class DisplayNameResolver {
	/**
	 * Resolves the display name of one identity.
	 *
	 * @param identity the technical identity, such as a scenario or player name
	 * @param metadata the identity's presentation metadata, if any
	 * @return the metadata's non-blank display name, else the identity with camel case and dashes or underscores
	 * split into words and the first letter in upper case
	 */
	public static @NotNull String resolve(@NotNull String identity, @Nullable PresentationMetadata metadata) {
		if (metadata != null && metadata.getDisplayName() != null && !metadata.getDisplayName().isBlank())
			return metadata.getDisplayName();

		String words = identity.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[-_]+", " ");
		return words.isEmpty() ? identity : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}
}
