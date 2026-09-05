package me.whereareiam.anvil.agent.common.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.operation.AgentOperationHandler;
import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.agent.common.transport.operation.TypedAgentOperation;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.common.transport.operation.AgentRequestHandler;
import me.whereareiam.anvil.agent.common.transport.operation.CommandOperation;
import me.whereareiam.anvil.agent.common.transport.operation.IdentityOperation;
import me.whereareiam.anvil.agent.common.transport.operation.PingOperation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dispatches transport requests to a typed {@link PlatformAgent}.
 *
 * <p>JSON is kept at this boundary. Platform integrations implement typed domain methods, while
 * the selected agent runtime only deals with decoded transport requests.</p>
 */
final class PlatformAgentRequestDispatcher {
	private final PlatformAgent platformAgent;
	private final Map<String, AgentRequestHandler> operations = new LinkedHashMap<>();
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Creates a request dispatcher for a platform agent.
	 *
	 * @param platformAgent typed platform behavior
	 */
	PlatformAgentRequestDispatcher(@NotNull PlatformAgent platformAgent) {
		this(platformAgent, List.of());
	}

	PlatformAgentRequestDispatcher(
			@NotNull PlatformAgent platformAgent,
			@NotNull Collection<AgentOperationProvider> providers
	) {
		this.platformAgent = platformAgent;
		List.of(new PingOperation(), new IdentityOperation(), new CommandOperation()).forEach(this::register);
		Set<String> installed = new HashSet<>();
		for (AgentOperationProvider provider : providers) {
			if (!provider.supports(platformAgent.info()))
				continue;
			String id = provider.id();
			if (!id.matches("[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)+"))
				throw new IllegalArgumentException("Agent operation provider needs a qualified namespace: " + id);
			if (!installed.add(id))
				throw new IllegalArgumentException("Duplicate agent operation provider: " + id);
			Registration registration = new Registration(id);
			try {
				provider.install(registration);
			} finally {
				registration.active = false;
			}
		}
	}

	/**
	 * Handles one operation from the authenticated agent connection.
	 *
	 * @param operation stable operation name
	 * @param arguments operation arguments
	 * @return JSON operation result
	 */
	public @NotNull JsonNode handle(@NotNull String operation, @NotNull JsonNode arguments) throws Exception {
		AgentRequestHandler operationHandler = operations.get(operation);
		if (operationHandler == null)
			throw new IllegalArgumentException("Unknown platform agent operation: " + operation);
		return operationHandler.handle(platformAgent, arguments);
	}

	private void register(AgentRequestHandler handler) {
		if (operations.putIfAbsent(handler.wireName(), handler) != null)
			throw new IllegalArgumentException("Duplicate agent operation: " + handler.wireName());
	}

	@RequiredArgsConstructor
	private final class Registration implements AgentOperationRegistry {
		private final String namespace;
		private final Thread owner = Thread.currentThread();
		private boolean active = true;

		@Override
		public <Q, R> void register(@NotNull AgentOperation<Q, R> operation, @NotNull AgentOperationHandler<Q, R> handler) {
			if (!active || Thread.currentThread() != owner)
				throw new IllegalStateException("Agent operations can only be registered during provider installation");
			if (!operation.getName().startsWith(namespace + ".") || operation.getName().length() == namespace.length() + 1)
				throw new IllegalArgumentException("Agent operation must belong to namespace '" + namespace + "': "
						+ operation.getName());
			PlatformAgentRequestDispatcher.this.register(new TypedAgentOperation<>(operation, handler, mapper));
		}
	}
}
