package me.whereareiam.anvil.launcher.adapter.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaPackageSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Supplies Java catalog documents and pinned archives through the assembled acquisition policy.
 */
@RequiredArgsConstructor
public final class ArtifactJavaPackageSource implements JavaPackageSource {
	private final @NotNull ArtifactResolver artifacts;

	@Override
	public @NotNull String catalog(@NotNull URI uri, @NotNull Predicate<String> usable) throws IOException {
		return artifacts.read(uri, usable);
	}

	@Override
	public @NotNull Path archive(@NotNull JavaArchive archive, @NotNull Path destination) throws IOException {
		return artifacts.obtain(archive.getUri(), destination, archive.getSha256());
	}
}
