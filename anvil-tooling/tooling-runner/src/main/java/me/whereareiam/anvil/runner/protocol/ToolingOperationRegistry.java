package me.whereareiam.anvil.runner.protocol;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.tooling.api.EnvironmentOperations;
import me.whereareiam.anvil.tooling.api.ProcessOperations;
import me.whereareiam.anvil.tooling.api.ScenarioOperations;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
			String definition = request.definition() == null ? request.scenario() : request.definition();
			String target = request.target();
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
			session.startProcess(requiredText(request.target(), "target"));
			return session.snapshot();
		});
		register(ProcessOperations.STOP, Policy.CANCELLABLE, request -> {
			session.stopProcess(requiredText(request.target(), "target"));
			return session.snapshot();
		});
		register(ProcessOperations.RESTART, Policy.CANCELLABLE, request -> {
			session.restartProcess(requiredText(request.target(), "target"));
			return session.snapshot();
		});
		register(ProcessOperations.CONSOLE, request -> {
			session.console(requiredText(request.target(), "target"), requiredText(request.text(), "text"));
			return ConsoleCommandResult.builder().accepted(true).build();
		});
		register(EnvironmentOperations.INVOKE, Policy.CANCELLABLE, request -> {
			requiredText(request.sessionId(), "sessionId");
			requiredText(request.actionId(), "actionId");
			requiredText(request.target() == null ? null : request.target().name(), "target.name");
			request.arguments().values().forEach(value -> Objects.requireNonNull(value, "Action arguments must be strings"));
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
		String name = requiredText(operation.name(), "operation");
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
			Q payload = reader.decode(request, operation.requestType());
			return Objects.requireNonNull(handler.apply(payload),
					"Missing response for tooling operation '" + operation.name() + "'");
		}
	}
}
