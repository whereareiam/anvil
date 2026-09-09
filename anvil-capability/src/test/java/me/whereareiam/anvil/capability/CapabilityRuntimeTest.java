package me.whereareiam.anvil.capability;

import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRuntimeTest {
	@Test
	void composesNonPlayerCapabilitiesInDependencyOrderWithIndependentOwnerLifetimes() {
		List<String> closed = new ArrayList<>();
		AtomicInteger identities = new AtomicInteger();
		var status = provider("status", Status.class, Set.of(), context -> {
			int id = identities.incrementAndGet();
			context.onClose(() -> closed.add("status-" + id));
			return () -> id;
		});
		var command = provider("command", Command.class, Set.of(Status.class), context -> {
			Status dependency = context.requireCapability(Status.class);
			context.onClose(() -> closed.add("command-" + dependency.identity()));
			return dependency::identity;
		});
		var runtime = runtime(List.of(command, status));
		var first = runtime.compose("Agent 'lobby'", Function.identity());
		var second = runtime.compose("Agent 'proxy'", Function.identity());

		assertEquals(List.of("status", "command"), runtime.providers().stream().map(CapabilityDescriptor::getId).toList());
		assertEquals(1, first.capability(Command.class).execute());
		assertEquals(2, second.capability(Command.class).execute());
		assertNotSame(first.capability(Status.class), second.capability(Status.class));
		assertTrue(first.hasCapability(Command.class));

		first.close();
		first.close();
		assertEquals(List.of("command-1", "status-1"), closed);
		assertEquals(2, second.capability(Command.class).execute());
		second.close();
		assertEquals(List.of("command-1", "status-1", "command-2", "status-2"), closed);
	}

	@Test
	void doesNotExposeUndeclaredDependenciesAndNamesTheOwnerInDiagnostics() {
		var status = provider("status", Status.class, Set.of(), context -> () -> 1);
		var command = provider("command", Command.class, Set.of(), context -> {
			assertTrue(context.findCapability(Status.class).isEmpty());
			CapabilityException failure = assertThrows(CapabilityException.class,
					() -> context.requireCapability(Status.class));
			assertTrue(failure.getMessage().contains("Agent 'proxy'"));
			assertTrue(failure.getMessage().contains("command"));
			return () -> 2;
		});
		try (var capabilities = runtime(List.of(status, command)).compose("Agent 'proxy'", Function.identity())) {
			assertEquals(2, capabilities.capability(Command.class).execute());
			assertFalse(capabilities.hasCapability(Other.class));
		}
	}

	@Test
	void rejectsDependenciesBelongingToAnotherOwnerBeforeComposition() {
		var provider = provider("status", Status.class, Set.of(PlayerOnly.class), context -> () -> 1);
		CapabilityException failure = assertThrows(CapabilityException.class, () -> runtime(List.of(provider)));

		assertTrue(failure.getMessage().contains("outside owner type"));
		assertTrue(failure.getMessage().contains(DeviceCapability.class.getName()));
		assertTrue(failure.getMessage().contains(PlayerOnly.class.getName()));
	}

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void rejectsProvidersWithAClassOutsideTheOwnerDespiteUncheckedRegistration() {
		CapabilityProvider provider = new CapabilityProvider<PlayerOnly, CapabilityContext<DeviceCapability>>() {
			public @NotNull CapabilityDescriptor descriptor() {
				return CapabilityDescriptor.builder().id("player-only").build();
			}

			public @NotNull Class<PlayerOnly> capability() {
				return PlayerOnly.class;
			}

			public @NotNull PlayerOnly create(@NotNull CapabilityContext<DeviceCapability> context) {
				throw new AssertionError("Invalid owner must be rejected before creation");
			}
		};
		assertThrows(CapabilityException.class,
				() -> new CapabilityRuntime(DeviceCapability.class, List.of(provider)));
	}

	@Test
	void rollsBackPartialCreationWhenAnErrorOccursAndAttemptsEveryCleanup() {
		List<String> closed = new ArrayList<>();
		AssertionError construction = new AssertionError("construction failed");
		AssertionError cleanup = new AssertionError("cleanup failed");
		var status = provider("status", Status.class, Set.of(), context -> {
			context.onClose(() -> closed.add("status"));
			return () -> 1;
		});
		var command = provider("command", Command.class, Set.of(Status.class), context -> {
			context.onClose(() -> closed.add("command-first"));
			context.onClose(() -> {
				closed.add("command-last");
				throw cleanup;
			});
			throw construction;
		});

		AssertionError failure = assertThrows(AssertionError.class,
				() -> runtime(List.of(command, status)).compose("Agent 'lobby'", Function.identity()));
		assertSame(construction, failure);
		assertSame(cleanup, failure.getSuppressed()[0]);
		assertEquals(List.of("command-last", "command-first", "status"), closed);
	}

	@Test
	void rollsBackCleanupRegisteredByAFailingContextFactory() {
		List<String> closed = new ArrayList<>();
		AssertionError construction = new AssertionError("context failed");
		var provider = provider("status", Status.class, Set.of(), context -> {
			throw new AssertionError("Context factory must fail first");
		});

		assertSame(construction, assertThrows(AssertionError.class,
				() -> runtime(List.of(provider)).compose("Agent 'lobby'", context -> {
					context.onClose(() -> closed.add("context"));
					throw construction;
				})));
		assertEquals(List.of("context"), closed);
	}

	@Test
	void closesAllActionsWhenCleanupMixesErrorsAndRuntimeExceptions() {
		List<String> closed = new ArrayList<>();
		AssertionError first = new AssertionError("first failure");
		RuntimeException second = new IllegalStateException("second failure");
		var provider = provider("status", Status.class, Set.of(), context -> {
			context.onClose(() -> closed.add("oldest"));
			context.onClose(() -> {
				closed.add("second");
				throw second;
			});
			context.onClose(() -> {
				closed.add("first");
				throw first;
			});
			return () -> 1;
		});
		var capabilities = runtime(List.of(provider)).compose("Agent 'lobby'", Function.identity());

		assertSame(first, assertThrows(AssertionError.class, capabilities::close));
		assertSame(second, first.getSuppressed()[0]);
		assertEquals(List.of("first", "second", "oldest"), closed);
		capabilities.close();
		assertEquals(3, closed.size());
	}

	@Test
	void rejectsNullCapabilityAndReleasesItsRegisteredResources() {
		List<String> closed = new ArrayList<>();
		var provider = provider("status", Status.class, Set.of(), context -> {
			context.onClose(() -> closed.add("status"));
			return null;
		});

		CapabilityException failure = assertThrows(CapabilityException.class,
				() -> runtime(List.of(provider)).compose("Agent 'lobby'", Function.identity()));
		assertTrue(failure.getMessage().contains("returned null"));
		assertTrue(failure.getMessage().contains("Agent 'lobby'"));
		assertEquals(List.of("status"), closed);
	}

	@Test
	void rejectsDuplicateIdsAndCapabilityTypesBeforeCreation() {
		var status = provider("shared", Status.class, Set.of(), context -> () -> 1);
		var command = provider("shared", Command.class, Set.of(), context -> () -> 1);
		var duplicate = provider("other-status", Status.class, Set.of(), context -> () -> 2);

		assertTrue(assertThrows(CapabilityException.class, () -> runtime(List.of(status, command)))
				.getMessage().contains("Duplicate Anvil provider ID"));
		assertTrue(assertThrows(CapabilityException.class, () -> runtime(List.of(status, duplicate)))
				.getMessage().contains("provided by both"));
	}

	private CapabilityRuntime<DeviceCapability, CapabilityContext<DeviceCapability>> runtime(
			List<? extends CapabilityProvider<? extends DeviceCapability, CapabilityContext<DeviceCapability>>> providers
	) {
		return new CapabilityRuntime<>(DeviceCapability.class, providers);
	}

	private <C extends DeviceCapability> CapabilityProvider<C, CapabilityContext<DeviceCapability>> provider(
			String id,
			Class<C> type,
			Set<Class<? extends Capability>> dependencies,
			Function<CapabilityContext<DeviceCapability>, C> factory
	) {
		return new CapabilityProvider<>() {
			@Override
			public @NotNull CapabilityDescriptor descriptor() {
				return CapabilityDescriptor.builder().id(id).requiredCapabilities(dependencies).build();
			}

			@Override
			public @NotNull Class<C> capability() {
				return type;
			}

			@Override
			public @NotNull C create(@NotNull CapabilityContext<DeviceCapability> context) {
				return factory.apply(context);
			}
		};
	}

	private interface DeviceCapability extends Capability { }

	private interface Status extends DeviceCapability {
		int identity();
	}

	private interface Command extends DeviceCapability {
		int execute();
	}

	private interface Other extends DeviceCapability { }

	private interface PlayerOnly extends PlayerCapability { }
}
