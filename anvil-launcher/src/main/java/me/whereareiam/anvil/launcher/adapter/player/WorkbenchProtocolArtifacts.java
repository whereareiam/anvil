package me.whereareiam.anvil.launcher.adapter.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.workbench.api.Workbench;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Path;

/**
 * Resolves a protocol library's pinned artifacts, such as its worker's runtime closure, on the workbench where the
 * worker runs.
 */
@RequiredArgsConstructor
public final class WorkbenchProtocolArtifacts implements ProtocolArtifactResolver {
	private final @NotNull Workbench workbench;

	@Override
	public @NotNull Path resolve(@NotNull URI artifact, @NotNull Path destination, @NotNull String sha256) {
		return workbench.artifacts().obtain(artifact, destination, sha256);
	}
}
