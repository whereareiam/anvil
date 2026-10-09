package me.whereareiam.anvil.runner.extension;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionAvailability;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import me.whereareiam.anvil.tooling.extension.api.ToolingExtension;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistration;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistry;
import me.whereareiam.anvil.tooling.api.type.ObservationTone;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.process.ProcessAction;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.player.PlayerAction;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.ScenarioAction;
import me.whereareiam.anvil.tooling.extension.api.action.ToolingAction;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.process.ProcessObservation;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.player.PlayerObservation;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.ScenarioObservation;
import me.whereareiam.anvil.tooling.extension.api.observation.ToolingObservation;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Validates and owns tooling contributions, then binds them to current runtime targets.
 */
public final class ToolingExtensionRegistry implements ToolingRegistry {
	private static final TargetScope<ScenarioAccess> SCENARIO = new TargetScope<>(
			ActionTargetType.SCENARIO,
			List::of, context -> context.definition().getName(), target -> true);
	private static final TargetScope<RunningProcess> PROCESS = new TargetScope<>(
			ActionTargetType.PROCESS,
			context -> List.copyOf(context.processes().all()), RunningProcess::name,
			target -> target.state() == ProcessState.READY);
	private static final TargetScope<SimulatedPlayer> PLAYER = new TargetScope<>(
			ActionTargetType.PLAYER,
			context -> List.copyOf(context.players().all()), SimulatedPlayer::name,
			target -> !target.state().destroyed());

	private final Map<String, ActionBinding<?>> actions = new LinkedHashMap<>();
	private final Map<String, ObservationBinding<?>> observations = new LinkedHashMap<>();

	/**
	 * Registers extensions synchronously. Each extension receives a registration object that closes
	 * as soon as its callback returns.
	 *
	 * @param extensions extension implementations discovered on the project runtime
	 */
	public ToolingExtensionRegistry(@NotNull Iterable<ToolingExtension> extensions) {
		for (ToolingExtension extension : extensions)
			try (Registration registration = new Registration()) {
				extension.register(registration);
			}
	}

	/**
	 * Discovers extensions through the current context classloader.
	 *
	 * @return validated registry
	 */
	public static @NotNull ToolingExtensionRegistry discover() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return new ToolingExtensionRegistry(ServiceLoader.load(ToolingExtension.class,
				loader == null ? ToolingExtensionRegistry.class.getClassLoader() : loader));
	}

	/**
	 * Describes currently applicable actions.
	 *
	 * @param context borrowed scenario context
	 * @return action descriptors
	 */
	@Override
	public @NotNull List<ActionDescriptor> actions(@NotNull ScenarioContext context) {
		return actions.values().stream().flatMap(action -> action.describe(context).stream()).toList();
	}

	/**
	 * Evaluates currently applicable observations.
	 *
	 * @param context borrowed scenario context
	 * @return observation descriptors
	 */
	@Override
	public @NotNull List<ObservationDescriptor> observations(@NotNull ScenarioContext context) {
		return observations.values().stream().flatMap(observation -> observation.describe(context).stream()).toList();
	}

	/**
	 * Resolves, validates, and invokes one current action target.
	 *
	 * @param context borrowed scenario context
	 * @param request request belonging to the active tooling session
	 * @return validated portable result
	 */
	@Override
	public @NotNull ActionResult invoke(@NotNull ScenarioContext context, @NotNull ActionRequest request) {
		ActionBinding<?> action = actions.get(request.getActionId());
		if (action == null) throw new NoSuchElementException("Unknown tooling action: " + request.getActionId());

		ActionResult supplied = Objects.requireNonNull(action.invoke(context, request), "Action returned no result");
		ActionResult result = supplied.toBuilder().clearRows().rows(supplied.getRows().stream().map(List::copyOf).toList()).build();
		if (result.getRows().stream().anyMatch(row -> row.size() != result.getColumns().size())) {
			throw new IllegalStateException("Action result row does not match its columns: " + request.getActionId());
		}

		return result;
	}

	private void addAction(@NotNull ToolingAction<?> contribution) {
		ActionDefinition definition = ToolingDefinitionValidator.action(contribution.getDefinition());
		ActionBinding<?> bound = switch (contribution) {
			case ScenarioAction action -> new ActionBinding<>(SCENARIO, definition, action);
			case ProcessAction action -> new ActionBinding<>(PROCESS, definition, action);
			case PlayerAction action -> new ActionBinding<>(PLAYER, definition, action);
			default -> throw new IllegalArgumentException(
					"Unsupported tooling action scope; extend ScenarioAction, PlayerAction or ProcessAction.");
		};

		if (actions.putIfAbsent(definition.getId(), bound) != null) {
			throw new IllegalArgumentException("Duplicate tooling action: " + definition.getId());
		}
	}

	private void addObservation(@NotNull ToolingObservation<?> contribution) {
		ObservationDefinition definition = contribution.getDefinition();
		ToolingDefinitionValidator.observation(definition.getId());
		ObservationBinding<?> bound = switch (contribution) {
			case ScenarioObservation observation -> new ObservationBinding<>(SCENARIO, definition, observation);
			case ProcessObservation observation -> new ObservationBinding<>(PROCESS, definition, observation);
			case PlayerObservation observation -> new ObservationBinding<>(PLAYER, definition, observation);
			default -> throw new IllegalArgumentException(
					"Unsupported tooling observation scope; extend ScenarioObservation, PlayerObservation or ProcessObservation.");
		};

		if (observations.putIfAbsent(definition.getId(), bound) != null) {
			throw new IllegalArgumentException("Duplicate tooling observation: " + definition.getId());
		}
	}

	private final class Registration implements ToolingRegistration, AutoCloseable {
		private final Thread owner = Thread.currentThread();
		private boolean open = true;

		@Override
		public void action(@NotNull ToolingAction<?> action) {
			ensureOpen();
			addAction(action);
		}

		@Override
		public void observation(@NotNull ToolingObservation<?> observation) {
			ensureOpen();
			addObservation(observation);
		}

		@Override
		public void close() {
			open = false;
		}

		private void ensureOpen() {
			if (!open || Thread.currentThread() != owner)
				throw new IllegalStateException("Tooling registration is closed or accessed from another thread");
		}
	}

	/** Resolves one typed tooling target family from a live scenario context. */
	@RequiredArgsConstructor
	private static final class TargetScope<T> {
		private final @NotNull ActionTargetType type;
		private final @NotNull Function<ScenarioContext, List<T>> targets;
		private final @NotNull Function<T, String> name;
		private final @NotNull Predicate<T> ready;

		private @NotNull ActionTargetType type() {
			return type;
		}

		private @NotNull List<T> targets(@NotNull ScenarioContext context) {
			return targets.apply(context);
		}

		private @NotNull String name(@NotNull T target) {
			return name.apply(target);
		}

		private boolean ready(@NotNull T target) {
			return ready.test(target);
		}

		private @NotNull ActionTarget descriptor(@NotNull T target) {
			return ActionTarget.builder().type(type).name(name(target)).build();
		}
	}

	@RequiredArgsConstructor
	private static final class ActionBinding<T> {
		private final @NotNull TargetScope<T> scope;
		private final @NotNull ActionDefinition definition;
		private final @NotNull ToolingAction<T> handler;

		/**
		 * Describes supported targets. A failing support check is shown as an unavailable action rather
		 * than propagated, so one contribution cannot break snapshots or the environment lifecycle.
		 */
		private @NotNull List<ActionDescriptor> describe(@NotNull ScenarioContext context) {
			List<ActionDescriptor> described = new ArrayList<>();
			for (T target : scope.targets(context)) {
				ActionAvailability availability;
				try {
					if (!handler.supports(target)) continue;
					availability = availability(target);
				} catch (RuntimeException failure) {
					availability = ActionAvailability.builder().enabled(false)
							.reason("Support check failed: " + failure).build();
				}

				described.add(ActionDescriptor.builder()
						.definition(definition)
						.target(scope.descriptor(target))
						.availability(availability)
						.build());
			}

			return described;
		}

		private @NotNull ActionResult invoke(@NotNull ScenarioContext context, @NotNull ActionRequest request) {
			if (scope.type() != request.getTarget().getType()) throw new IllegalArgumentException("Action target type does not match");
			T target = scope.targets(context).stream().filter(value -> scope.name(value).equals(request.getTarget().getName()))
					.findFirst().orElseThrow(() -> new NoSuchElementException("Action target no longer exists: " + request.getTarget().getName()));
			if (!handler.supports(target)) throw new IllegalStateException("Target no longer supports this action");
			ActionAvailability availability = availability(target);
			if (!availability.isEnabled()) throw new IllegalStateException(availability.getReason() == null ? "Action is unavailable" : availability.getReason());
			return handler.execute(target, ToolingDefinitionValidator.arguments(definition, request.getArguments()));
		}

		private @NotNull ActionAvailability availability(@NotNull T target) {
			if (!scope.ready(target)) return ActionAvailability.builder().enabled(false).reason("Target is not ready").build();
			try {
				return Objects.requireNonNull(handler.availability(target), "Action returned no availability");
			} catch (RuntimeException failure) {
				return ActionAvailability.builder().enabled(false).reason("Availability check failed: " + failure).build();
			}
		}
	}

	@RequiredArgsConstructor
	private static final class ObservationBinding<T> {
		private final @NotNull TargetScope<T> scope;
		private final @NotNull ObservationDefinition definition;
		private final @NotNull ToolingObservation<T> handler;

		/**
		 * Describes supported targets. Support and observation failures remain visible as error values.
		 */
		private @NotNull List<ObservationDescriptor> describe(@NotNull ScenarioContext context) {
			List<ObservationDescriptor> described = new ArrayList<>();
			for (T target : scope.targets(context)) {
				ObservationValue value;
				try {
					if (!handler.supports(target)) continue;
					value = scope.ready(target)
							? Objects.requireNonNull(handler.observe(target), "Observation returned no value")
							: ObservationValue.builder().text("Target is not ready").build();
				} catch (RuntimeException failure) {
					value = ObservationValue.builder().text(failure.toString())
							.tone(ObservationTone.ERROR).build();
				}

				described.add(ObservationDescriptor.builder()
						.definition(definition)
						.target(scope.descriptor(target))
						.value(value)
						.build());
			}

			return described;
		}
	}
}
