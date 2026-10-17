package me.whereareiam.anvil.launcher.assembly;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.launcher.adapter.player.CapabilityPlayerComposerProvider;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerCompositionTest {
	private static final String PROVIDER_DESCRIPTOR = "META-INF/services/" + ProtocolPlayerComposerProvider.class.getName();
	private static final ProtocolPlayerComposer COMPOSER = (player, connection, metadata, onDestroyed) -> {
		throw new AssertionError("Discovery must not create a player");
	};

	@Test
	void composesThePlayersOfEachLibraryWithItsOwnProviders(@TempDir Path directory) throws Exception {
		String featureDescriptor = "META-INF/services/" + ProtocolPlayerCapabilityProvider.class.getName();
		Path descriptor = directory.resolve(featureDescriptor);
		Files.createDirectories(descriptor.getParent());
		Files.writeString(descriptor, SelectedFeatureProvider.class.getName());

		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader()) {
			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals(featureDescriptor)) return findResources(name);
				if (name.startsWith("META-INF/services/")) return Collections.emptyEnumeration();
				return super.getResources(name);
			}
		}) {
			thread.setContextClassLoader(loader);
			var composer = PlayerComposition.create(List.of("selected"), new CapabilityPlayerComposerProvider());
			var selected = composer.compose(new StubPlayer("selected"), PlayerConnection.to("server"), null, ignored -> {});
			try (AutoCloseable selectedCleanup = selected::destroy) {
				assertTrue(selected.hasCapability(SelectedFeature.class));
				var other = composer.compose(new StubPlayer("other"), PlayerConnection.to("server"), null, ignored -> {});
				try (AutoCloseable otherCleanup = other::destroy) {
					assertFalse(other.hasCapability(SelectedFeature.class));
				}
			}
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	@Test
	void preservesAnExternalProtocolOnlyComposer() {
		assertSame(COMPOSER, PlayerComposition.create(List.of("external-library"), new ExternalComposerProvider()));
	}

	@Test
	void instantiatesOnlyTheFirstDiscoveredComposer(@TempDir Path directory) throws IOException {
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
			assertSame(COMPOSER, PlayerComposition.create(List.of("external-library")));
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	public static final class SelectedFeatureProvider implements ProtocolPlayerCapabilityProvider<SelectedFeature> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("test.selected-feature").build();
		}

		@Override
		public @NotNull Set<String> supportedLibraries() {
			return Set.of("selected");
		}

		@Override
		public @NotNull Class<SelectedFeature> capability() {
			return SelectedFeature.class;
		}

		@Override
		public @NotNull SelectedFeature create(@NotNull ProtocolPlayerCapabilityContext context) {
			return new SelectedFeature();
		}
	}

	private static final class SelectedFeature implements PlayerCapability { }

	@RequiredArgsConstructor
	private static final class StubPlayer implements ProtocolPlayer {
		private final @NotNull String libraryId;
		private boolean destroyed;

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public @NotNull String libraryId() {
			return libraryId;
		}

		@Override
		public @NotNull ProtocolRelease release() {
			MinecraftVersion version = MinecraftVersion.parse("1.21.11");
			return ProtocolRelease.builder().libraryVersion("test").minecraftVersion(version).protocolNumber(774).javaVersion(21).build();
		}

		@Override
		public @NotNull PlayerProfile requestedProfile() {
			return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes()));
		}

		@Override
		public @Nullable PlayerProfile grantedProfile() {
			return null;
		}

		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return Optional.empty();
		}

		@Override
		public boolean destroyed() {
			return destroyed;
		}

		@Override
		public void destroy() {
			destroyed = true;
		}
	}

	public static final class ExternalComposerProvider implements ProtocolPlayerComposerProvider {
		@Override
		public @NotNull String id() {
			return "external";
		}

		@Override
		public @NotNull ProtocolPlayerComposer create() {
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
