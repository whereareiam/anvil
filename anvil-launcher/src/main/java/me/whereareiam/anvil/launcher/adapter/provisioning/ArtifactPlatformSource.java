package me.whereareiam.anvil.launcher.adapter.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.workbench.api.Workbench;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Binds platform distribution and catalog requests to the workbench that runs the processes.
 */
@RequiredArgsConstructor
public final class ArtifactPlatformSource implements PlatformArtifactSource {
	private final @NotNull Workbench workbench;

	@Override
	public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String checksum) {
		return workbench.artifacts().obtain(uri, destination, checksum);
	}

	@Override
	public @NotNull String read(@NotNull URI uri) {
		return workbench.artifacts().read(uri);
	}

	@Override
	public @NotNull Path store(@NotNull Path file) {
		return workbench.store(file);
	}
}
