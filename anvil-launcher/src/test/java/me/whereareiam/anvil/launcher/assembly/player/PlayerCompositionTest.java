package me.whereareiam.anvil.launcher.assembly.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.client.AgentPlayerObservation;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerCompositionTest {
	private static final String PROVIDER_DESCRIPTOR = "META-INF/services/" + ProtocolPlayerComposerProvider.class.getName();
	private static final ProtocolPlayerComposer COMPOSER = (player, observation, onDestroyed) -> {
		throw new AssertionError("Discovery must not create a player");
	};

	@Test
	void bindsScenarioAgentsToTheBuiltInComposerAndKeepsStandaloneCreationUnscoped(@TempDir Path directory) throws Exception {
		String agentDescriptor = "META-INF/services/" + AgentPlayerCapabilityProvider.class.getName();
		Path descriptor = directory.resolve(agentDescriptor);
		Files.createDirectories(descriptor.getParent());
		Files.writeString(descriptor, AgentFeatureProvider.class.getName());

		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader()) {
			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals(agentDescriptor)) return findResources(name);
				if (name.startsWith("META-INF/services/")) return Collections.emptyEnumeration();
				return super.getResources(name);
			}
		}) {
			thread.setContextClassLoader(loader);
			AgentDirectory agents = () -> Map.of("server", new StubAgent());
			var provider = new CapabilityPlayerComposerProvider();
			var protocol = new StubPlayer();
			var observation = new AgentPlayerObservation(protocol.name(), protocol::identity, Map::of, Set.of());
			var scoped = PlayerComposition.create("selected", agents, provider).compose(protocol, observation, ignored -> {});
			try (AutoCloseable scopedCleanup = scoped::destroy) {
				assertNotNull(scoped.capability(AgentFeature.class).context.channel("server"));
				var standalone = provider.create("selected").compose(new StubPlayer(), observation, ignored -> {});
				try (AutoCloseable standaloneCleanup = standalone::destroy) {
					var failure = assertThrows(CapabilityException.class,
							() -> standalone.capability(AgentFeature.class).context.channel("server"));
					assertTrue(failure.getMessage().contains("server"));
					assertTrue(failure.getMessage().contains("agent request channel"));
				}
			}
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	@Test
	void preservesAnExternalProtocolOnlyComposer() {
		assertSame(COMPOSER, PlayerComposition.create("external-backend", Map::of, new ExternalComposerProvider()));
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
			assertSame(COMPOSER, PlayerComposition.create("external-backend", Map::of));
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	public static final class AgentFeatureProvider implements AgentPlayerCapabilityProvider<AgentFeature> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("test.agent-feature").build();
		}

		@Override
		public @NotNull Class<AgentFeature> capability() {
			return AgentFeature.class;
		}

		@Override
		public @NotNull AgentFeature create(@NotNull AgentPlayerCapabilityContext context) {
			return new AgentFeature(context);
		}
	}

	@RequiredArgsConstructor
	private static final class AgentFeature implements PlayerCapability {
		private final @NotNull AgentPlayerCapabilityContext context;
	}

	private static final class StubAgent implements AgentClient {
		@Override
		public <T> @Nullable T request(@NotNull String operation, @Nullable Object arguments, @NotNull Class<T> responseType) {
			throw new AssertionError("Composition must not send agent requests");
		}

		@Override
		public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
			return Optional.empty();
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			throw new AssertionError("Composition must not execute commands");
		}

		@Override
		public void close() {
			throw new AssertionError("Composition borrows scenario agents");
		}
	}

	private static final class StubPlayer implements ProtocolPlayer {
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
		public @NotNull PlayerIdentity identity() {
			throw new AssertionError("Composition must not observe identity");
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
		public @NotNull ProtocolPlayerComposer create(@NotNull String protocolId) {
			assertEquals("external-backend", protocolId);
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
		public @NotNull ProtocolPlayerComposer create(@NotNull String protocolId) {
			throw new AssertionError("An unused composer must not be selected");
		}
	}
}
