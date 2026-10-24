package me.whereareiam.anvil.workbench.remote.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * An execution session on a workbench on another machine.
 */
@RequiredArgsConstructor
final class RemoteExecutionSession implements ExecutionSession {
	private static final TypeReference<Map<String, InetSocketAddress>> ADDRESSES = new TypeReference<>() {
	};

	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String handle;
	private final @NotNull InetAddress mirrorAddress;

	@Override
	public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
		ObjectMapper mapper = connection.mapper();
		var arguments = mapper.createObjectNode().put("session", handle);
		arguments.set("request", mapper.valueToTree(request));
		JsonNode target = connection.call("session.prepare", arguments);

		return new RemoteProcessTarget(
				connection,
				target.path("handle").asText(),
				mapper.convertValue(target.path("address"), InetSocketAddress.class),
				mapper.convertValue(target.path("peerAddress"), InetSocketAddress.class),
				target.path("bindAddress").asText(),
				mapper.convertValue(target.path("agentAddress"), InetSocketAddress.class),
				target.path("agentPort").asInt(),
				target.path("agentBindAddress").asText(),
				mapper.convertValue(target.path("listeners"), ADDRESSES),
				mapper.convertValue(target.path("peerListeners"), ADDRESSES),
				mirrorAddress
		);
	}

	@Override
	public void close() {
		connection.call("session.close", connection.mapper().createObjectNode().put("handle", handle));
	}
}
