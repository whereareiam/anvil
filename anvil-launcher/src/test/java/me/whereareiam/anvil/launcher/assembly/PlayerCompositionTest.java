package me.whereareiam.anvil.launcher.assembly;

import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class PlayerCompositionTest {
	private static final String PROVIDER_DESCRIPTOR = "META-INF/services/" + ProtocolPlayerComposerProvider.class.getName();
	private static final ProtocolPlayerComposer COMPOSER = (player, connection, metadata, onDestroyed) -> {
		throw new AssertionError("Discovery must not create a player");
	};

	@Test
	void createsOnlyTheFirstDiscoveredComposerForTheScenarioLibraries(@TempDir Path directory) throws IOException {
		Path descriptor = directory.resolve(PROVIDER_DESCRIPTOR);
		Files.createDirectories(descriptor.getParent());
		Files.write(descriptor, List.of(ExternalComposerProvider.class.getName(), UnusedComposerProvider.class.getName()));

		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader()) {
			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals(PROVIDER_DESCRIPTOR)) return findResources(name);
				return super.getResources(name);
			}
		}) {
			thread.setContextClassLoader(loader);
			ExternalComposerProvider.libraries = null;

			assertSame(COMPOSER, PlayerComposition.create(List.of("external-library")));
			assertEquals(List.of("external-library"), ExternalComposerProvider.libraries);
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	public static final class ExternalComposerProvider implements ProtocolPlayerComposerProvider {
		private static Collection<String> libraries;

		@Override
		public @NotNull String id() {
			return "external";
		}

		@Override
		public @NotNull ProtocolPlayerComposer create() {
			throw new AssertionError("A scenario composer receives the scenario's libraries");
		}

		@Override
		public @NotNull ProtocolPlayerComposer create(@NotNull Collection<String> libraries) {
			ExternalComposerProvider.libraries = libraries;
			return COMPOSER;
		}
	}

	public static final class UnusedComposerProvider implements ProtocolPlayerComposerProvider {
		public UnusedComposerProvider() {
			throw new AssertionError("An unused composer must not be instantiated");
		}

		@Override
		public @NotNull String id() {
			return "unused";
		}

		@Override
		public @NotNull ProtocolPlayerComposer create() {
			throw new AssertionError("An unused composer must not be selected");
		}
	}
}
