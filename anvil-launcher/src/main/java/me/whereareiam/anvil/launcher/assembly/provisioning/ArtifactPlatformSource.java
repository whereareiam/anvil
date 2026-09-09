package me.whereareiam.anvil.launcher.assembly.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Binds platform distribution and catalog requests to the selected artifact acquisition policy.
 */
@RequiredArgsConstructor
public final class ArtifactPlatformSource implements PlatformArtifactSource {
	private final @NotNull ArtifactResolver artifacts;

	@Override
	public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String checksum) {
		return artifacts.obtain(uri, destination, checksum);
	}

	@Override
	public @NotNull String read(@NotNull URI uri) {
		return artifacts.read(uri);
	}
}
