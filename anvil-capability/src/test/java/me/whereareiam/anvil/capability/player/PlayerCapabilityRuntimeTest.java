package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.player.CapabilityPlayer;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolCapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCapabilityRuntimeTest {
	@Test
	void discoversBuiltinsSolelyFromTheirRuntimeDependencies() {
		assertEquals(Set.of(
				"me.whereareiam.anvil.session",
				"me.whereareiam.anvil.server",
				"me.whereareiam.anvil.messages",
				"me.whereareiam.anvil.movement",
				"me.whereareiam.anvil.inventory",
				"me.whereareiam.anvil.interaction"
		), PlayerCapabilityRuntime.discover().providers().stream()
				.map(CapabilityDescriptor::getId)
				.collect(Collectors.toSet()));
	}

	@Test
	void packetFeaturesComposeIndependentlyWithoutInstallingSession() {
		Set<String> independent = Set.of("me.whereareiam.anvil.movement", "me.whereareiam.anvil.messages",
				"me.whereareiam.anvil.inventory", "me.whereareiam.anvil.interaction");
		int tested = 0;
		for (ProtocolPlayerCapabilityProvider<?> provider : ServiceLoader.load(ProtocolPlayerCapabilityProvider.class)) {
			if (!independent.contains(provider.descriptor().getId())) continue;
			StubPlayer backend = new StubPlayer();
			backend.channel = new CapabilityChannel() {
				public <Q, R> R request(@NotNull ChannelOperation<Q, R> channelOperation, Q request) { throw new AssertionError("Creation must not execute packets"); }
				public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) { return () -> { }; }
				public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) { throw new AssertionError(); }
				public @NotNull Set<String> installedCapabilities() { return Set.of(provider.descriptor().getId()); }
			};
			SimulatedPlayer player = new PlayerCapabilityRuntime(List.of(), List.of(provider))
					.compose(backend, observation(), ignored -> { });
			assertTrue(player.hasCapability(provider.capability()));
			player.destroy();
			tested++;
		}
		assertEquals(4, tested);
	}

	@Test
	void validatesDiscoveredDependenciesAfterAddingAssemblyProviders(@TempDir Path directory) throws Exception {
		Path service = directory.resolve("META-INF/services/" + ProtocolPlayerCapabilityProvider.class.getName());
		Files.createDirectories(service.getParent());
		Files.writeString(service, DiscoveredDependent.class.getName());
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try (var loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, original)) {
			Thread.currentThread().setContextClassLoader(loader);
			assertThrows(CapabilityException.class, () -> PlayerCapabilityRuntime.discover("additional-test"));
			var runtime = PlayerCapabilityRuntime.discover("additional-test", List.of(new BaseProvider()));
			var ids = runtime.providers().stream().map(CapabilityDescriptor::getId).toList();
			assertTrue(ids.indexOf("base") < ids.indexOf("discovered-dependent"));
		} finally { Thread.currentThread().setContextClassLoader(original); }
	}

	public static final class DiscoveredDependent implements ProtocolPlayerCapabilityProvider<DependentCapability> {
		public @NotNull Set<String> supportedProtocolIds() {
			return Set.of("additional-test");
		}
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("discovered-dependent")
					.requiredCapability(BaseCapability.class).build();
		}
		public @NotNull Class<DependentCapability> capability() { return DependentCapability.class; }
		public @NotNull DependentCapability create(@NotNull ProtocolPlayerCapabilityContext context) { return () -> "dependent"; }
	}

	@Test
	void validatesSharedAndProtocolFactoriesAsOneGraphBeforeCreatingCapabilities() {
		AtomicInteger created = new AtomicInteger();
		var base = sharedProvider("base", BaseCapability.class, Set.of(), context -> {
			created.incrementAndGet();
			return () -> "base";
		});
		var dependent = provider("dependent", DependentCapability.class, Set.of(BaseCapability.class), context -> {
			created.incrementAndGet();
			assertEquals("service", context.requireService(String.class));
			BaseCapability dependency = context.requireCapability(BaseCapability.class);
			return () -> dependency.value() + "-dependent";
		});
		var summary = sharedProvider("summary", SummaryCapability.class, Set.of(DependentCapability.class), context -> {
			created.incrementAndGet();
			DependentCapability dependency = context.requireCapability(DependentCapability.class);
			return () -> dependency.value() + "-summary";
		});
		assertThrows(CapabilityException.class, () -> new PlayerCapabilityRuntime(List.of(summary), List.of(dependent)));
		PlayerCapabilityRuntime runtime = new PlayerCapabilityRuntime(List.of(summary, base), List.of(dependent));
		assertEquals(0, created.get(), "Graph validation must not construct shared or protocol capabilities");
		StubPlayer driven = new StubPlayer();
		AtomicBoolean removed = new AtomicBoolean();

		SimulatedPlayer player = runtime.compose(driven, observation(), ignored -> removed.set(true));

		assertEquals(List.of("base", "dependent", "summary"), runtime.providers().stream()
				.map(CapabilityDescriptor::getId)
				.toList());
		assertEquals(3, created.get());
		assertEquals("base-dependent", player.capability(DependentCapability.class).value());
		assertEquals("base-dependent-summary", player.capability(SummaryCapability.class).value());
		assertTrue(player.hasCapability(BaseCapability.class));
		player.destroy();
		assertTrue(player.state().destroyed());
		assertTrue(driven.destroyed());
		assertTrue(removed.get());
	}

	@Test
	void composesSharedCapabilitiesWithoutProtocolChannelsOrSdkServices() {
		List<String> released = new ArrayList<>();
		PlayerObservation observation = observation();
		var provider = sharedProvider("identity", BaseCapability.class, Set.of(), context -> {
			assertFalse(context instanceof ProtocolPlayerCapabilityContext);
			assertEquals("Alice", context.playerName());
			assertEquals("test", context.clientVersion());
			assertSame(observation, context.observation());
			context.onClose(() -> released.add("shared"));
			return () -> context.observation().identity().getUsername();
		});
		var backend = new PlainPlayer();
		SimulatedPlayer player = new PlayerCapabilityRuntime(List.of(provider))
				.compose(backend, observation, ignored -> released.add("removed"));

		assertEquals("Alice", player.capability(BaseCapability.class).value());
		assertFalse(player.state().destroyed());
		player.destroy();
		player.destroy();
		assertTrue(backend.destroyed());
		assertTrue(player.state().destroyed());
		assertEquals(List.of("shared", "removed"), released);
	}

	@Test
	void rejectsProtocolFactoriesWhenThePlayerSuppliesOnlySharedInputs() {
		AtomicBoolean created = new AtomicBoolean();
		var provider = provider("native", BaseCapability.class, Set.of(), context -> {
			created.set(true);
			return () -> "native";
		});
		var runtime = new PlayerCapabilityRuntime(List.of(), List.of(provider));

		var failure = assertThrows(CapabilityException.class,
				() -> runtime.compose(new PlainPlayer(), observation(), ignored -> { }));
		assertTrue(failure.getMessage().contains("requires protocol player inputs"));
		assertFalse(created.get());
	}

	@Test
	void rejectsMissingAndCyclicCapabilityDependencies() {
		CapabilityException missing = assertThrows(CapabilityException.class,
				() -> new PlayerCapabilityRuntime(List.of(), List.of(new DependentProvider())));
		assertTrue(missing.getMessage().contains("missing capabilities"));

		CapabilityException cycle = assertThrows(CapabilityException.class,
				() -> new PlayerCapabilityRuntime(List.of(), List.of(
						new NamedProvider<>("one", TwoCapability.class, OneCapability.class, OneCapability::new),
						new NamedProvider<>("two", OneCapability.class, TwoCapability.class, TwoCapability::new)
				)));
		assertTrue(cycle.getMessage().contains("Cyclic Anvil capability-provider dependency"));
	}

	@Test
	void reportsMissingCapabilitiesThroughTheGlobalApi() {
		SimulatedPlayer player = new PlayerCapabilityRuntime(List.of())
				.compose(new StubPlayer(), observation(), ignored -> { });

		CapabilityUnavailableException failure = assertThrows(
				CapabilityUnavailableException.class,
				() -> player.capability(BaseCapability.class)
		);
		assertTrue(failure.getMessage().contains(BaseCapability.class.getName()));
	}

	@Test
	void hidesUndeclaredCapabilitiesRegardlessOfCreationOrder() {
		ProtocolPlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(),
				context -> () -> "base");
		ProtocolPlayerCapabilityProvider<DependentCapability> dependent = provider("dependent", DependentCapability.class,
				Set.of(), context -> {
					assertTrue(context.findCapability(BaseCapability.class).isEmpty());
					return () -> "independent";
				});
		for (var providers : List.of(List.of(base, dependent), List.of(dependent, base)))
			new PlayerCapabilityRuntime(List.of(), providers).compose(new StubPlayer(), observation(), ignored -> { }).destroy();
	}

	@Test
	void rollsBackRegisteredCleanupWhenCapabilityConstructionFails() {
		List<String> released = new ArrayList<>();
		RuntimeException constructionFailure = new CapabilityException("construction failed");
		RuntimeException cleanupFailure = new IllegalStateException("cleanup failed");
		ProtocolPlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(), context -> {
			context.onDestroy(() -> released.add("base"));
			return () -> "base";
		});
		ProtocolPlayerCapabilityProvider<DependentCapability> failing = provider("dependent", DependentCapability.class,
				Set.of(BaseCapability.class), context -> {
					context.onDestroy(() -> {
						released.add("dependent");
						throw cleanupFailure;
					});
					throw constructionFailure;
				});
		RuntimeException failure = assertThrows(CapabilityException.class,
				() -> new PlayerCapabilityRuntime(List.of(), List.of(failing, base))
						.compose(new StubPlayer(), observation(), ignored -> { }));
		assertSame(constructionFailure, failure);
		assertSame(cleanupFailure, failure.getSuppressed()[0]);
		assertEquals(List.of("dependent", "base"), released);
	}

	@Test
	void reportsCleanupFailuresAfterReleasingAllCapabilitiesAndRemovingPlayer() {
		List<String> released = new ArrayList<>();
		RuntimeException cleanupFailure = new IllegalStateException("cleanup failed");
		ProtocolPlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(), context -> {
			context.onDestroy(() -> released.add("base"));
			context.onDestroy(() -> { throw cleanupFailure; });
			return () -> "base";
		});
		StubPlayer driven = new StubPlayer();
		SimulatedPlayer player = new PlayerCapabilityRuntime(List.of(), List.of(base))
				.compose(driven, observation(), ignored -> released.add("removed"));
		assertSame(cleanupFailure, assertThrows(RuntimeException.class, player::destroy));
		assertTrue(driven.destroyed());
		assertEquals(List.of("base", "removed"), released);
		player.destroy();
		assertEquals(2, released.size());
	}

	@Test
	void destroysCapabilitiesAndRemovesPlayerEvenWhenBackendDestructionThrowsAnError() {
		List<String> released = new ArrayList<>();
		AssertionError backendFailure = new AssertionError("backend failed");
		RuntimeException cleanupFailure = new IllegalStateException("capability cleanup failed");
		ProtocolPlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(), context -> {
			context.onClose(() -> {
				released.add("capability");
				throw cleanupFailure;
			});
			return () -> "base";
		});
		StubPlayer backend = new StubPlayer();
		backend.destroyFailure = backendFailure;
		SimulatedPlayer player = new PlayerCapabilityRuntime(List.of(), List.of(base))
				.compose(backend, observation(), ignored -> released.add("removed"));

		assertSame(backendFailure, assertThrows(AssertionError.class, player::destroy));
		assertSame(cleanupFailure, backendFailure.getSuppressed()[0]);
		assertTrue(backend.destroyed());
		assertTrue(player.state().destroyed());
		assertEquals(List.of("capability", "removed"), released);
		player.destroy();
		assertEquals(2, released.size());
	}

	private static PlayerObservation observation() {
		PlayerIdentity identity = PlayerIdentity.builder().username("Alice")
				.clientUniqueId(UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8))).build();
		return new PlayerObservation() {
			@Override
			public PlayerIdentity identity() { return identity; }
			@Override
			public PlayerIdentity await(Predicate<PlayerIdentity> condition, Duration timeout) { return identity(); }
		};
	}

	private <C extends PlayerCapability> ProtocolPlayerCapabilityProvider<C> provider(
			String id,
			Class<C> type,
			Set<Class<? extends PlayerCapability>> dependencies,
			Function<ProtocolPlayerCapabilityContext, C> factory
	) {
		return new ProtocolPlayerCapabilityProvider<>() {
			@Override
			public @NotNull CapabilityDescriptor descriptor() {
				return CapabilityDescriptor.builder().id(id).requiredCapabilities(dependencies).build();
			}

			@Override
			public @NotNull Class<C> capability() {
				return type;
			}

			@Override
			public @NotNull C create(@NotNull ProtocolPlayerCapabilityContext context) {
				return factory.apply(context);
			}
		};
	}

	private <C extends PlayerCapability> PlayerCapabilityProvider<C> sharedProvider(
			String id,
			Class<C> type,
			Set<Class<? extends PlayerCapability>> dependencies,
			Function<PlayerCapabilityContext, C> factory
	) {
		return new PlayerCapabilityProvider<>() {
			@Override
			public @NotNull CapabilityDescriptor descriptor() {
				return CapabilityDescriptor.builder().id(id).requiredCapabilities(dependencies).build();
			}

			@Override
			public @NotNull Class<C> capability() {
				return type;
			}

			@Override
			public @NotNull C create(@NotNull PlayerCapabilityContext context) {
				return factory.apply(context);
			}
		};
	}

	private interface BaseCapability extends PlayerCapability {
		String value();
	}

	private interface DependentCapability extends PlayerCapability {
		String value();
	}

	private interface SummaryCapability extends PlayerCapability {
		String value();
	}

	private static final class OneCapability implements PlayerCapability { }

	private static final class TwoCapability implements PlayerCapability { }

	private static final class BaseProvider implements PlayerCapabilityProvider<BaseCapability> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("base").build();
		}

		@Override
		public @NotNull Class<BaseCapability> capability() {
			return BaseCapability.class;
		}

		@Override
		public @NotNull BaseCapability create(@NotNull PlayerCapabilityContext context) {
			return () -> "base";
		}
	}

	private static final class DependentProvider implements ProtocolPlayerCapabilityProvider<DependentCapability> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("dependent").requiredCapability(BaseCapability.class).build();
		}

		@Override
		public @NotNull Class<DependentCapability> capability() {
			return DependentCapability.class;
		}

		@Override
		public @NotNull DependentCapability create(@NotNull ProtocolPlayerCapabilityContext context) {
			BaseCapability base = context.requireCapability(BaseCapability.class);
			return () -> base.value() + "-dependent";
		}
	}

	private static final class NamedProvider<C extends PlayerCapability> implements ProtocolPlayerCapabilityProvider<C> {
		private final String id;
		private final Class<? extends PlayerCapability> dependency;
		private final Class<C> capability;
		private final Supplier<C> factory;

		private NamedProvider(
				String id,
				Class<? extends PlayerCapability> dependency,
				Class<C> capability,
				Supplier<C> factory
		) {
			this.id = id;
			this.dependency = dependency;
			this.capability = capability;
			this.factory = factory;
		}

		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id(id).requiredCapability(dependency).build();
		}

		@Override
		public @NotNull Class<C> capability() {
			return capability;
		}

		@Override
		public @NotNull C create(@NotNull ProtocolPlayerCapabilityContext context) {
			return factory.get();
		}
	}

	private static final class StubPlayer implements ProtocolCapabilityPlayer {
		private boolean destroyed;
		private CapabilityChannel channel;
		private Error destroyFailure;

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "test";
		}

		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return type == String.class ? Optional.of(type.cast("service")) : Optional.empty();
		}

		@Override
		public @NotNull Optional<CapabilityChannel> channel() { return Optional.ofNullable(channel); }

		@Override
		public boolean destroyed() {
			return destroyed;
		}

		@Override
		public void destroy() {
			destroyed = true;
			if (destroyFailure != null) throw destroyFailure;
		}
	}

	private static final class PlainPlayer implements CapabilityPlayer {
		private boolean destroyed;

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "test";
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
}
