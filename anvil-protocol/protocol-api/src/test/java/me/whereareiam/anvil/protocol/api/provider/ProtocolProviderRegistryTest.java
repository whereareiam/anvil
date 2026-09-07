package me.whereareiam.anvil.protocol.api.provider;

import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolProviderRegistryTest {
	private final ArtifactResolver artifacts = new ArtifactResolver() {
		@Override
		public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, String checksum) {
			throw new AssertionError("Provider selection must not download artifacts");
		}

		@Override
		public @NotNull String read(@NotNull URI uri) {
			throw new AssertionError("Provider selection must not query metadata");
		}
	};

	@Test
	void selectsTheSoleProviderWithoutCreatingItsBackend() {
		AtomicInteger creations = new AtomicInteger();
		ProtocolProvider provider = new StubProvider("sole") {
			@Override
			public @NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ArtifactResolver artifacts) {
				creations.incrementAndGet();
				return super.create(cacheDirectory, artifacts);
			}
		};

		ProtocolProviderRegistry registry = new ProtocolProviderRegistry(List.of(provider));
		assertEquals(provider, registry.select(null));
		assertEquals(0, creations.get());
		assertEquals("sole", registry.create(null, Path.of("cache"), artifacts).id());
		assertEquals(1, creations.get());
	}

	@Test
	void selectsExplicitProviderWhenSeveralAreInstalled() {
		ProtocolBackend backend = new StubBackend("second");
		ProtocolProviderRegistry registry = new ProtocolProviderRegistry(List.of(
				new StubProvider("first"),
				new StubProvider("second") {
					@Override
					public @NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ArtifactResolver artifacts) {
						return backend;
					}
				}
		));

		assertEquals(List.of("first", "second"), registry.ids().stream().toList());
		assertEquals("second", registry.create("second", Path.of("cache"), artifacts).id());
	}

	@Test
	void rejectsAmbiguousAndUnknownSelection() {
		ProtocolProviderRegistry registry = new ProtocolProviderRegistry(List.of(
				new StubProvider("first"),
				new StubProvider("second")
		));

		assertThrows(IllegalStateException.class, () -> registry.create(null, Path.of("cache"), artifacts));
		assertThrows(IllegalArgumentException.class, () -> registry.create("missing", Path.of("cache"), artifacts));
	}

	private static class StubProvider implements ProtocolProvider {
		private final String id;

		private StubProvider(String id) {
			this.id = id;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ArtifactResolver artifacts) {
			return new StubBackend(id);
		}
	}

	private static final class StubBackend implements ProtocolBackend {
		private final String id;

		private StubBackend(String id) {
			this.id = id;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull Collection<ProtocolSupport> supportedProtocols() {
			return List.of();
		}

		@Override
		public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void close() { }
	}
}
