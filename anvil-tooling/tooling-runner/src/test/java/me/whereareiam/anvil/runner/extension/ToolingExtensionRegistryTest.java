package me.whereareiam.anvil.runner.extension;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.tooling.extension.api.action.ToolingAction;
import me.whereareiam.anvil.tooling.extension.api.observation.ToolingObservation;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.model.action.binding.*;
import me.whereareiam.anvil.tooling.api.model.action.definition.*;
import me.whereareiam.anvil.tooling.api.model.action.invocation.*;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import me.whereareiam.anvil.tooling.api.type.ObservationTone;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.ScenarioAction;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.process.ProcessCapabilityAction;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.ScenarioObservation;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.process.ProcessCapabilityObservation;
import me.whereareiam.anvil.tooling.extension.api.ToolingExtension;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistration;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import org.jetbrains.annotations.NotNull;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ToolingExtensionRegistryTest {
	private final AtomicInteger count = new AtomicInteger();
	private final AtomicReference<ProcessState> state = new AtomicReference<>(ProcessState.READY);
	private boolean supported = true;
	private RuntimeException capabilityFailure;
	private final Counter counter = new CountingCounter(count);
	private Counter activeCounter = counter;
	private final AtomicReference<Counter> checkedCapability = new AtomicReference<>();
	private final RunningProcess process = proxy(RunningProcess.class, (target, method, arguments) -> switch (method.getName()) {
		case "name" -> "proxy";
		case "state" -> state.get();
		case "hasCapability" -> {
			if (capabilityFailure != null) throw capabilityFailure;
			yield supported && arguments[0] == Counter.class;
		}
		case "capability" -> activeCounter;
		default -> throw new AssertionError(method.getName());
	});
	private List<RunningProcess> processes = List.of(process);
	private final ScenarioProcesses group = proxy(ScenarioProcesses.class, (target, method, arguments) -> {
		if (method.getName().equals("all")) return processes;
		throw new AssertionError(method.getName());
	});
	private final PlayerManager players = proxy(PlayerManager.class, (target, method, arguments) -> List.of());
	private final ScenarioContext context = proxy(ScenarioContext.class, (target, method, arguments) -> switch (method.getName()) {
		case "definition" -> AnvilScenario.builder().name("fixture").entrypoint("proxy").build();
		case "processes" -> group;
		case "players" -> players;
		default -> throw new AssertionError(method.getName());
	});

	@Test
	void invokesAnExternalCapabilityAndReturnsPortableResults() {
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(extension()));
		ActionDescriptor action = registry.actions(context).getFirst();
		assertEquals("fixture.counter.add", action.getDefinition().getId());
		assertTrue(action.getAvailability().isEnabled());
		assertEquals(ActionTargetType.PROCESS, action.getTarget().getType());
		assertEquals("0", registry.observations(context).getFirst().getValue().getText());

		ActionResult result = registry.invoke(context, request(Map.of("amount", "3")));
		assertEquals(List.of("Count"), result.getColumns());
		assertEquals(List.of(List.of("3")), result.getRows());
		assertEquals("3", registry.observations(context).getFirst().getValue().getText());
	}

	@Test
	void failingSupportCheckIsReportedAsAValueInsteadOfBreakingSnapshots() {
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(extension()));
		capabilityFailure = new IllegalStateException("Capability lookup failed");

		ActionDescriptor action = assertDoesNotThrow(() -> registry.actions(context)).getFirst();
		ObservationDescriptor observation = assertDoesNotThrow(() -> registry.observations(context)).getFirst();

		assertFalse(action.getAvailability().isEnabled());
		assertTrue(action.getAvailability().getReason().contains("Capability lookup failed"));
		assertEquals(ObservationTone.ERROR, observation.getValue().getTone());
		assertTrue(observation.getValue().getText().contains("Capability lookup failed"));
	}

	@Test
	void rechecksReadinessCapabilitiesAndTargetExistenceAtInvocation() {
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(extension()));
		assertTrue(registry.actions(context).getFirst().getAvailability().isEnabled());
		state.set(ProcessState.STOPPED);
		assertFalse(registry.actions(context).getFirst().getAvailability().isEnabled());
		assertThrows(IllegalStateException.class, () -> registry.invoke(context, request(Map.of())));
		state.set(ProcessState.READY);
		supported = false;
		assertTrue(registry.actions(context).isEmpty());
		assertThrows(IllegalStateException.class, () -> registry.invoke(context, request(Map.of())));
		supported = true;
		processes = List.of();
		assertThrows(NoSuchElementException.class, () -> registry.invoke(context, request(Map.of())));
		assertEquals(0, count.get());
	}

	@Test
	void validatesDefaultsTypesAndUnknownInputsBeforeCallingHandlers() {
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(extension()));
		assertThrows(IllegalArgumentException.class, () -> registry.invoke(context, request(Map.of("amount", "not-a-number"))));
		assertThrows(IllegalArgumentException.class, () -> registry.invoke(context, request(Map.of("unknown", "1"))));
		assertThrows(IllegalArgumentException.class, () -> registry.invoke(context, request(Map.of()).toBuilder()
				.target(ActionTarget.builder().type(ActionTargetType.PLAYER).name("proxy").build()).build()));
		assertEquals(0, count.get());
		registry.invoke(context, request(Map.of()));
		assertEquals(1, count.get());
	}

	@Test
	void rejectsDuplicateAndLateRegistration() {
		assertThrows(IllegalArgumentException.class, () -> new ToolingExtensionRegistry(List.of(extension(), extension())));
		AtomicReference<ToolingRegistration> retained = new AtomicReference<>();
		new ToolingExtensionRegistry(List.of(retained::set));
		assertThrows(IllegalStateException.class, () -> retained.get().action(noop(ActionDefinition.builder().id("fixture.late").build())));
		assertThrows(IllegalStateException.class, () -> retained.get().observation(new ScenarioObservation(
				ObservationDefinition.builder().id("fixture.late").build()) {
			@Override
			public @NotNull ObservationValue observe(@NotNull ScenarioAccess target) { throw new AssertionError(); }
		}));
		assertThrows(IllegalArgumentException.class, () -> new ToolingExtensionRegistry(List.of(registration ->
				registration.action(noop(ActionDefinition.builder().id("unnamespaced").build())))));
	}

	@Test
	void resolvesTheCurrentTypedCapabilityForAvailabilityExecutionAndObservation() {
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(extension()));
		registry.actions(context);
		assertSame(counter, checkedCapability.get());
		AtomicInteger replacementCount = new AtomicInteger(10);
		activeCounter = new CountingCounter(replacementCount);

		ActionResult result = registry.invoke(context, request(Map.of("amount", "2")));
		assertSame(activeCounter, checkedCapability.get());
		assertEquals(List.of(List.of("12")), result.getRows());
		assertEquals("12", registry.observations(context).getFirst().getValue().getText());
		assertEquals(0, count.get());
	}

	@Test
	void rejectsContributionsFromAnotherThreadWhileRegistrationIsOpen() {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(registration -> {
			Thread other = Thread.ofVirtual().start(() -> {
				try { registration.action(noop(ActionDefinition.builder().id("fixture.thread").build())); }
				catch (Throwable rejected) { failure.set(rejected); }
			});
			assertDoesNotThrow(() -> { other.join(); });
		}));
		assertInstanceOf(IllegalStateException.class, failure.get());
		assertTrue(registry.actions(context).isEmpty());
	}

	@Test
	void surfacesObservationAndAvailabilityFailuresWithoutLosingOtherContributions() {
		var failure = new IllegalStateException("Fixture observation failed");
		ToolingExtensionRegistry registry = new ToolingExtensionRegistry(List.of(extension(), registration -> {
			registration.observation(new ScenarioObservation(ObservationDefinition.builder().id("fixture.failure").build()) {
				@Override
				public @NotNull ObservationValue observe(@NotNull ScenarioAccess target) { throw failure; }
			});
			registration.action(new ScenarioAction(ActionDefinition.builder().id("fixture.unavailable").build()) {
				@Override public @NotNull ActionAvailability availability(@NotNull ScenarioAccess target) { throw failure; }
				@Override public @NotNull ActionResult execute(@NotNull ScenarioAccess target, @NotNull ToolingArguments arguments) { throw new AssertionError(); }
			});
		}));
		assertEquals(2, registry.observations(context).size());
		assertEquals(ObservationTone.ERROR, registry.observations(context).getLast().getValue().getTone());
		assertFalse(registry.actions(context).getLast().getAvailability().isEnabled());
		assertTrue(registry.actions(context).getFirst().getAvailability().isEnabled());
	}

	@Test
	void rejectsInvalidChoiceDefaultsAndSensitiveNonTextInputs() {
		for (ActionInput input : List.of(
				ActionInput.builder().name("choice").type(ActionInputType.CHOICE).build(),
				ActionInput.builder().name("number").type(ActionInputType.INTEGER).defaultValue("x").build(),
				ActionInput.builder().name("secret").type(ActionInputType.BOOLEAN).sensitive(true).build()))
			assertThrows(IllegalArgumentException.class, () -> new ToolingExtensionRegistry(List.of(registration -> registration.action(
					noop(ActionDefinition.builder().id("fixture.invalid").inputs(List.of(input)).build())))));
	}

	@Test
	void rejectsActionsWithoutASupportedScopeDuringRegistration() {
		var action = new ToolingAction<Object>(ActionDefinition.builder().id("fixture.unscoped").build()) {
			@Override
			public @NotNull ActionResult execute(@NotNull Object target, @NotNull ToolingArguments arguments) {
				throw new AssertionError("An unscoped action must never execute");
			}
		};

		var failure = assertThrows(IllegalArgumentException.class,
				() -> new ToolingExtensionRegistry(List.of(registration -> registration.action(action))));
		assertTrue(failure.getMessage().contains("Unsupported tooling action scope"));
	}

	@Test
	void rejectsObservationsWithoutASupportedScopeDuringRegistration() {
		var observation = new ToolingObservation<Object>(ObservationDefinition.builder().id("fixture.unscoped").build()) {
			@Override
			public @NotNull ObservationValue observe(@NotNull Object target) {
				throw new AssertionError("An unscoped observation must never execute");
			}
		};

		var failure = assertThrows(IllegalArgumentException.class,
				() -> new ToolingExtensionRegistry(List.of(registration -> registration.observation(observation))));
		assertTrue(failure.getMessage().contains("Unsupported tooling observation scope"));
	}

	private ToolingExtension extension() {
		return registration -> {
			registration.action(new ProcessCapabilityAction<>(Counter.class, ActionDefinition.builder().id("fixture.counter.add")
					.inputs(List.of(ActionInput.builder().name("amount").type(ActionInputType.INTEGER).defaultValue("1").build())).build()) {
				@Override
				public @NotNull ActionAvailability availability(@NotNull RunningProcess target, @NotNull Counter capability) {
					checkedCapability.set(capability);
					return super.availability(target, capability);
				}

				@Override
				public @NotNull ActionResult execute(@NotNull RunningProcess target, @NotNull Counter capability, @NotNull ToolingArguments arguments) {
					return ActionResult.builder().columns(List.of("Count"))
							.rows(List.of(List.of(Integer.toString(capability.add((int) arguments.integer("amount")))))).build();
				}
			});
			registration.observation(new ProcessCapabilityObservation<>(Counter.class,
					ObservationDefinition.builder().id("fixture.counter.value").build()) {
				@Override
				public @NotNull ObservationValue observe(@NotNull RunningProcess target, @NotNull Counter capability) {
					return ObservationValue.builder().text(Integer.toString(capability.value())).build();
				}
			});
		};
	}

	private ScenarioAction noop(ActionDefinition definition) {
		return new ScenarioAction(definition) {
			@Override
			public @NotNull ActionResult execute(@NotNull ScenarioAccess target, @NotNull ToolingArguments arguments) {
				return ActionResult.builder().build();
			}
		};
	}

	private ActionRequest request(Map<String, String> arguments) {
		return ActionRequest.builder().sessionId("fixture-session").actionId("fixture.counter.add")
				.target(ActionTarget.builder().type(ActionTargetType.PROCESS).name("proxy").build()).arguments(arguments).build();
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}

	private interface Counter extends ProcessCapability {
		int add(int amount);
		int value();
	}

	@RequiredArgsConstructor
	private static final class CountingCounter implements Counter {
		private final AtomicInteger count;

		@Override
		public int add(int amount) { return count.addAndGet(amount); }

		@Override
		public int value() { return count.get(); }
	}
}
