package me.whereareiam.anvil.capability.runtime;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

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
	void ordersDependenciesAndComposesTypedCapabilities() {
		PlayerCapabilityRuntime runtime = new PlayerCapabilityRuntime(List.of(
				new DependentProvider(),
				new BaseProvider()
		));
		StubPlayer driven = new StubPlayer();
		AtomicBoolean removed = new AtomicBoolean();

		SimulatedPlayer player = runtime.create(driven, List.of("service"), ignored -> removed.set(true));

		assertEquals(List.of("base", "dependent"), runtime.providers().stream()
				.map(CapabilityDescriptor::getId)
				.toList());
		assertEquals("base-dependent", player.capability(DependentCapability.class).value());
		assertTrue(player.hasCapability(BaseCapability.class));
		player.destroy();
		assertTrue(player.state().destroyed());
		assertTrue(driven.destroyed());
		assertTrue(removed.get());
	}

	@Test
	void rejectsMissingAndCyclicCapabilityDependencies() {
		CapabilityException missing = assertThrows(CapabilityException.class,
				() -> new PlayerCapabilityRuntime(List.of(new DependentProvider())));
		assertTrue(missing.getMessage().contains("missing capabilities"));

		CapabilityException cycle = assertThrows(CapabilityException.class,
				() -> new PlayerCapabilityRuntime(List.of(
						new NamedProvider<>("one", TwoCapability.class, OneCapability.class, OneCapability::new),
						new NamedProvider<>("two", OneCapability.class, TwoCapability.class, TwoCapability::new)
				)));
		assertTrue(cycle.getMessage().contains("Cyclic Anvil capability-provider dependency"));
	}

	@Test
	void reportsMissingCapabilitiesThroughTheGlobalApi() {
		SimulatedPlayer player = new PlayerCapabilityRuntime(List.of())
				.create(new StubPlayer(), List.of(), ignored -> { });

		CapabilityUnavailableException failure = assertThrows(
				CapabilityUnavailableException.class,
				() -> player.capability(BaseCapability.class)
		);
		assertTrue(failure.getMessage().contains(BaseCapability.class.getName()));
	}

	@Test
	void hidesUndeclaredCapabilitiesRegardlessOfCreationOrder() {
		PlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(),
				context -> () -> "base");
		PlayerCapabilityProvider<DependentCapability> dependent = provider("dependent", DependentCapability.class,
				Set.of(), context -> {
					assertTrue(context.findCapability(BaseCapability.class).isEmpty());
					return () -> "independent";
				});
		for (var providers : List.of(List.of(base, dependent), List.of(dependent, base)))
			new PlayerCapabilityRuntime(providers).compose(new StubPlayer(), List.of(), ignored -> { }).destroy();
	}

	@Test
	void rollsBackRegisteredCleanupWhenCapabilityConstructionFails() {
		List<String> released = new ArrayList<>();
		RuntimeException constructionFailure = new CapabilityException("construction failed");
		RuntimeException cleanupFailure = new IllegalStateException("cleanup failed");
		PlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(), context -> {
			context.onDestroy(() -> released.add("base"));
			return () -> "base";
		});
		PlayerCapabilityProvider<DependentCapability> failing = provider("dependent", DependentCapability.class,
				Set.of(BaseCapability.class), context -> {
					context.onDestroy(() -> {
						released.add("dependent");
						throw cleanupFailure;
					});
					throw constructionFailure;
				});
		RuntimeException failure = assertThrows(CapabilityException.class,
				() -> new PlayerCapabilityRuntime(List.of(failing, base))
						.compose(new StubPlayer(), List.of(), ignored -> { }));
		assertSame(constructionFailure, failure);
		assertSame(cleanupFailure, failure.getSuppressed()[0]);
		assertEquals(List.of("dependent", "base"), released);
	}

	@Test
	void reportsCleanupFailuresAfterReleasingAllCapabilitiesAndRemovingPlayer() {
		List<String> released = new ArrayList<>();
		RuntimeException cleanupFailure = new IllegalStateException("cleanup failed");
		PlayerCapabilityProvider<BaseCapability> base = provider("base", BaseCapability.class, Set.of(), context -> {
			context.onDestroy(() -> released.add("base"));
			context.onDestroy(() -> { throw cleanupFailure; });
			return () -> "base";
		});
		StubPlayer driven = new StubPlayer();
		SimulatedPlayer player = new PlayerCapabilityRuntime(List.of(base))
				.compose(driven, List.of(), ignored -> released.add("removed"));
		assertSame(cleanupFailure, assertThrows(RuntimeException.class, player::destroy));
		assertTrue(driven.destroyed());
		assertEquals(List.of("base", "removed"), released);
		player.destroy();
		assertEquals(2, released.size());
	}

	private <C extends PlayerCapability> PlayerCapabilityProvider<C> provider(
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
			assertEquals("service", context.requireService(String.class));
			return () -> "base";
		}
	}

	private static final class DependentProvider implements PlayerCapabilityProvider<DependentCapability> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("dependent").requiredCapability(BaseCapability.class).build();
		}

		@Override
		public @NotNull Class<DependentCapability> capability() {
			return DependentCapability.class;
		}

		@Override
		public @NotNull DependentCapability create(@NotNull PlayerCapabilityContext context) {
			BaseCapability base = context.requireCapability(BaseCapability.class);
			return () -> base.value() + "-dependent";
		}
	}

	private static final class NamedProvider<C extends PlayerCapability> implements PlayerCapabilityProvider<C> {
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
		public @NotNull C create(@NotNull PlayerCapabilityContext context) {
			return factory.get();
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
			return "test";
		}

		@Override
		public @NotNull PlayerIdentity identity() {
			return PlayerIdentity.builder()
					.username(name())
					.clientUniqueId(UUID.nameUUIDFromBytes(name().getBytes(StandardCharsets.UTF_8)))
					.build();
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
}
