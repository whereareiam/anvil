package me.whereareiam.anvil.workbench.remote.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactInstaller;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ObtainArtifact;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ReadArtifact;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Downloads artifacts on a workbench on another machine, which keeps them in its own cache; nothing a scenario
 * downloads crosses to the engine.
 */
@RequiredArgsConstructor
public final class RemoteArtifacts implements ArtifactResolver {
	private final @NotNull WorkbenchConnection connection;

	@Override
	public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String expectedSha256) {
		return connection.call(new ObtainArtifact(uri, destination, expectedSha256)).getPath();
	}

	/**
	 * Refuses an installation: its installer runs in the engine's JVM and writes with the machine's own file
	 * operations, so it cannot install into the cache of a workbench on another machine. {@code RequirementCheck}
	 * refuses the platforms that install, such as NeoForge, before a scenario gets here.
	 */
	@Override
	public @NotNull Path install(@NotNull Path destination, @NotNull ArtifactInstaller installer) {
		throw new UnsupportedOperationException("A workbench on another machine cannot install " + destination
				+ "; installations run only on the workbench in the engine's own JVM");
	}

	/**
	 * Reads a document through the workbench's cache, refusing it when it is not usable.
	 */
	@Override
	public @NotNull String read(@NotNull URI uri, @NotNull Predicate<String> usable) {
		String document = connection.call(new ReadArtifact(uri)).getContent();
		if (!usable.test(document)) throw new IllegalStateException("The workbench returned an unusable document for " + uri);
		return document;
	}
}
