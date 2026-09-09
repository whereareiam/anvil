package me.whereareiam.anvil.launcher.assembly.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaPackageSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Supplies Java catalog documents and pinned archives through the assembled acquisition policy.
 */
@RequiredArgsConstructor
final class ArtifactJavaPackageSource implements JavaPackageSource {
	private final @NotNull ArtifactResolver artifacts;

	@Override
	public @NotNull String catalog(@NotNull URI uri) throws IOException {
		return artifacts.read(uri);
	}

	@Override
	public @NotNull Path archive(@NotNull JavaArchive archive, @NotNull Path destination) throws IOException {
		return artifacts.obtain(archive.getUri(), destination, archive.getSha256());
	}
}
