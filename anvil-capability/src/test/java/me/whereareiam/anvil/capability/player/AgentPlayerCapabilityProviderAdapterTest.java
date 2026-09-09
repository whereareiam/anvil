package me.whereareiam.anvil.capability.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlayerCapabilityProviderAdapterTest {
	private static final ChannelOperation<String, String> ECHO = new ChannelOperation<>(
			"external.feature.echo", String.class, String.class
	);

	@Test
	void routesChannelsToTheirOwnScenarioAndKeepsCleanupScopedToEachPlayer() {
		var first = new Channel("first");
		var second = new Channel("second");
		List<String> cleanup = new ArrayList<>();
		var provider = provider(CapabilityDescriptor.builder().id("external.agent-feature").build(), context -> {
			context.onClose(() -> cleanup.add(context.playerName()));
			return new Feature(context.channel("server"));
		});
		Context alice = new Context("Alice");
		Context bob = new Context("Bob");
		Feature aliceFeature = new AgentPlayerCapabilityProviderAdapter<>(provider, name -> first)
				.create(alice);
		Feature bobFeature = new AgentPlayerCapabilityProviderAdapter<>(provider, name -> second)
				.create(bob);

		assertEquals("first:hello", aliceFeature.echo("hello"));
		assertEquals("second:hello", bobFeature.echo("hello"));
		alice.cleanup.forEach(Runnable::run);
		assertEquals(List.of("Alice"), cleanup);
		assertEquals("second:still-running", bobFeature.echo("still-running"));
		bob.cleanup.forEach(Runnable::run);
		assertEquals(List.of("Alice", "Bob"), cleanup);
	}

	@Test
	void preservesTheDescriptorAndDelegatesDeclaredDependencyAccess() {
		Dependency dependency = () -> "dependency";
		CapabilityDescriptor descriptor = CapabilityDescriptor.builder().id("external.dependent")
				.requiredCapability(Dependency.class).build();
		var provider = provider(descriptor, context -> {
			assertSame(dependency, context.requireCapability(Dependency.class));
			assertTrue(context.findCapability(Unrelated.class).isEmpty());
			assertThrows(CapabilityException.class, () -> context.requireCapability(Unrelated.class));
			assertEquals("Alice", context.playerName());
			assertEquals("1.21.11", context.clientVersion());
			return new Feature(context.channel("server"));
		});
		var adapter = new AgentPlayerCapabilityProviderAdapter<>(provider, name -> new Channel("first"));
		Context alice = new Context("Alice");
		alice.dependencies.put(Dependency.class, dependency);

		assertSame(descriptor, adapter.descriptor());
		assertSame(Feature.class, adapter.capability());
		assertEquals("first:hello", adapter.create(alice).echo("hello"));
	}


	private AgentPlayerCapabilityProvider<Feature> provider(
			CapabilityDescriptor descriptor,
			Function<AgentPlayerCapabilityContext, Feature> factory
	) {
		return new AgentPlayerCapabilityProvider<>() {
			@Override
			public @NotNull CapabilityDescriptor descriptor() {
				return descriptor;
			}

			@Override
			public @NotNull Class<Feature> capability() {
				return Feature.class;
			}

			@Override
			public @NotNull Feature create(@NotNull AgentPlayerCapabilityContext context) {
				return factory.apply(context);
			}
		};
	}

	@RequiredArgsConstructor
	private static final class Feature implements PlayerCapability {
		private final RequestChannel channel;

		private String echo(String value) {
			return channel.request(ECHO, value);
		}
	}

	private interface Dependency extends PlayerCapability {
		String value();
	}

	private interface Unrelated extends PlayerCapability { }

	@RequiredArgsConstructor
	private static final class Channel implements RequestChannel {
		private final String name;

		@Override
		public @Nullable <Q, R> R request(@NotNull ChannelOperation<Q, R> operation, @Nullable Q request) {
			assertSame(ECHO, operation);
			return operation.getResponseType().cast(name + ":" + request);
		}
	}

	@RequiredArgsConstructor
	private static final class Context implements PlayerCapabilityContext {
		private final String name;
		private final Map<Class<? extends PlayerCapability>, PlayerCapability> dependencies = new HashMap<>();
		private final List<Runnable> cleanup = new ArrayList<>();

		@Override
		public @NotNull String playerName() {
			return name;
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public @NotNull PlayerObservation observation() {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull <C extends PlayerCapability> Optional<C> findCapability(@NotNull Class<C> type) {
			return Optional.ofNullable(dependencies.get(type)).map(type::cast);
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			cleanup.add(action);
		}
	}
}
