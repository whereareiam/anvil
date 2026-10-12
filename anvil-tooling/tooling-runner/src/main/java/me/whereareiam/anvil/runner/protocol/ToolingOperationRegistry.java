package me.whereareiam.anvil.runner.protocol;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandResult;
import me.whereareiam.anvil.tooling.api.EnvironmentOperations;
import me.whereareiam.anvil.tooling.api.ProcessOperations;
import me.whereareiam.anvil.tooling.api.ScenarioOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Binds shared operation schemas to session behavior and runner-owned cancellation policy.
 * Registration finishes before the protocol starts reading requests.
 */
final class ToolingOperationRegistry {
	private final @NotNull Map<String, Binding<?, ?>> operations = new LinkedHashMap<>();

	ToolingOperationRegistry(@NotNull ToolingSession session) {
		register(ScenarioOperations.DISCOVER, ignored -> session.scenarios());
		register(EnvironmentOperations.SNAPSHOT, ignored -> session.snapshot());
		register(ScenarioOperations.START, Policy.CANCELLABLE, request -> {
			String definition = request.getDefinition() == null ? request.getScenario() : request.getDefinition();
			String target = request.getTarget();
			if (target != null) requiredText(target, "target");
			session.start(requiredText(definition, "definition"), target);
			return session.snapshot();
		});
		register(EnvironmentOperations.START_ALL, Policy.CANCELLABLE, ignored -> {
			session.startAll();
			return session.snapshot();
		});
		register(EnvironmentOperations.STOP, Policy.CANCEL_PENDING, ignored -> {
			session.stop();
			return session.snapshot();
		});
		register(ProcessOperations.START, Policy.CANCELLABLE, request -> {
			session.startProcess(requiredText(request.getTarget(), "target"));
			return session.snapshot();
		});
		register(ProcessOperations.STOP, Policy.CANCELLABLE, request -> {
			session.stopProcess(requiredText(request.getTarget(), "target"));
			return session.snapshot();
		});
		register(ProcessOperations.RESTART, Policy.CANCELLABLE, request -> {
			session.restartProcess(requiredText(request.getTarget(), "target"));
			return session.snapshot();
		});
		register(ProcessOperations.CONSOLE, request -> {
			session.console(requiredText(request.getTarget(), "target"), requiredText(request.getText(), "text"));
			return ConsoleCommandResult.builder().accepted(true).build();
		});
		register(EnvironmentOperations.INVOKE, Policy.CANCELLABLE, request -> {
			requiredText(request.getSessionId(), "sessionId");
			requiredText(request.getActionId(), "actionId");
			requiredText(request.getTarget() == null ? null : request.getTarget().getName(), "target.name");
			request.getArguments().values().forEach(value -> Objects.requireNonNull(value, "Action arguments must be strings"));
			return session.invoke(request);
		});
	}

	@Nullable Binding<?, ?> find(@NotNull String name) {
		return operations.get(name);
	}

	<Q, R> void register(@NotNull ToolingOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
		register(operation, Policy.ORDERED, handler);
	}

	private <Q, R> void register(ToolingOperation<Q, R> operation, Policy policy, Function<Q, R> handler) {
		String name = requiredText(operation.getName(), "operation");
		if (operations.putIfAbsent(name, new Binding<>(operation, policy, handler)) != null)
			throw new IllegalArgumentException("Duplicate tooling operation: " + name);
	}

	private static @NotNull String requiredText(@Nullable String value, @NotNull String field) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing request field: " + field);
		return value;
	}

	private enum Policy {
		ORDERED,
		CANCELLABLE,
		CANCEL_PENDING
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static final class Binding<Q, R> {
		private final @NotNull ToolingOperation<Q, R> operation;
		private final @NotNull Policy policy;
		private final @NotNull Function<Q, R> handler;

		boolean cancellable() {
			return policy == Policy.CANCELLABLE;
		}

		boolean cancelsPending() {
			return policy == Policy.CANCEL_PENDING;
		}

		@NotNull R execute(
				@NotNull ToolingRequestReader.Request request,
				@NotNull ToolingRequestReader reader
		) throws IOException {
			Q payload = reader.decode(request, operation.getRequestType());
			return Objects.requireNonNull(handler.apply(payload),
					"Missing response for tooling operation '" + operation.getName() + "'");
		}
	}
}
