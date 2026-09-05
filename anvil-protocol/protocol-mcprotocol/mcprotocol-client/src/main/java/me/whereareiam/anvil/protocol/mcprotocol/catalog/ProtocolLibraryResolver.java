package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.protocol.mcprotocol.model.ProtocolDefinition;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the selected library's own dependency graph instead of borrowing another version's
 * PacketLib, Netty, or NBT classes from the host. The root JAR remains content-pinned by the catalog.
 */
public final class ProtocolLibraryResolver implements AutoCloseable {
	private final RepositorySystem repositories = new RepositorySystemSupplier().get();
	private final ProtocolArtifactResolver artifacts = new ProtocolArtifactResolver();
	private final List<RemoteRepository> sources = List.of(
			new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
			new RemoteRepository.Builder("opencollab-releases", "default", "https://repo.opencollab.dev/maven-releases/").build(),
			new RemoteRepository.Builder("opencollab-snapshots", "default", "https://repo.opencollab.dev/maven-snapshots/").build(),
			new RemoteRepository.Builder("jitpack", "default", "https://jitpack.io/").build()
	);

	public @NotNull List<Path> resolve(@NotNull ProtocolDefinition definition, @NotNull Path cacheDirectory) {
		Path protocol = artifacts.resolve(definition, cacheDirectory);
		var session = MavenRepositorySystemUtils.newSession();
		session.setLocalRepositoryManager(repositories.newLocalRepositoryManager(session,
				new LocalRepository(cacheDirectory.resolve("protocol/dependencies").toFile())));
		session.setReadOnly();
		var artifact = new DefaultArtifact(definition.getCoordinate());
		var collect = new CollectRequest(new Dependency(artifact, JavaScopes.RUNTIME), sources);
		try {
			var result = repositories.resolveDependencies(session,
					new DependencyRequest(collect, DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME)));
			List<Path> classpath = new ArrayList<>();
			classpath.add(protocol);
			String adventureVersion = result.getArtifactResults().stream().map(value -> value.getArtifact())
					.filter(value -> value.getGroupId().equals("net.kyori") && value.getArtifactId().equals("adventure-api"))
					.findFirst().orElseThrow(() -> new IllegalStateException("Protocol library is missing Adventure API"))
					.getVersion();
			var serializer = new DefaultArtifact("net.kyori:adventure-text-serializer-plain:" + adventureVersion);
			classpath.add(repositories.resolveArtifact(session, new ArtifactRequest(serializer, sources, null))
					.getArtifact().getFile().toPath());
			result.getArtifactResults().stream().map(value -> value.getArtifact())
					// PacketLib explicitly allows omitting its experimental io_uring transport.
					.filter(value -> !value.getGroupId().equals("io.netty.incubator"))
					.filter(value -> !value.getGroupId().equals(artifact.getGroupId())
							|| !value.getArtifactId().equals(artifact.getArtifactId()))
					.forEach(value -> classpath.add(value.getFile().toPath()));
			return List.copyOf(classpath);
		} catch (DependencyResolutionException | ArtifactResolutionException exception) {
			throw new IllegalStateException("Could not resolve native dependencies for " + definition.getCoordinate(), exception);
		}
	}

	@Override
	public void close() {
		repositories.shutdown();
	}
}
