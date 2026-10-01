package me.whereareiam.anvil.runner.scenario;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DisplayNameResolver {
	public static @NotNull String resolve(@NotNull String identity, @Nullable PresentationMetadata metadata) {
		if (metadata != null && metadata.getDisplayName() != null && !metadata.getDisplayName().isBlank())
			return metadata.getDisplayName();

		String words = identity.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[-_]+", " ");
		return words.isEmpty() ? identity : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}
}
