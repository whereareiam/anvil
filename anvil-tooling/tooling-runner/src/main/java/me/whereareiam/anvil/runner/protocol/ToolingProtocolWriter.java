package me.whereareiam.anvil.runner.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serializes typed frames atomically to one JSON-lines response stream.
 */
@RequiredArgsConstructor
final class ToolingProtocolWriter {
	private final @NotNull PrintWriter output;
	private final @NotNull ObjectMapper json = new ObjectMapper();

	void ready() {
		write(new Ready(Kind.READY, ToolingSession.PROTOCOL_VERSION));
	}

	void queued(@NotNull String id) {
		write(new Queued(Kind.QUEUED, id));
	}

	void success(@NotNull String id, @NotNull Object result) {
		write(new Response(Kind.RESPONSE, id, true, result, null));
	}

	void failure(@NotNull String id, @NotNull Throwable failure) {
		write(new Response(Kind.RESPONSE, id, false, null, failure.toString()));
	}

	void error(@NotNull Throwable failure) {
		write(new Failure(Kind.ERROR, failure.toString()));
	}

	void snapshot(@NotNull SessionSnapshot snapshot) {
		write(new Snapshot(Kind.SNAPSHOT, snapshot));
	}

	void log(@NotNull LogEvent event) {
		write(new Output(Kind.LOG, event));
	}

	private synchronized void write(Frame frame) {
		try {
			output.println(json.writeValueAsString(frame));
			output.flush();
		} catch (IOException failure) {
			throw new IllegalStateException("Could not encode tooling response", failure);
		}
	}

	private enum Kind {
		@JsonProperty("ready") READY,
		@JsonProperty("queued") QUEUED,
		@JsonProperty("response") RESPONSE,
		@JsonProperty("error") ERROR,
		@JsonProperty("snapshot") SNAPSHOT,
		@JsonProperty("log") LOG
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private sealed interface Frame {}
	private record Ready(Kind type, int protocolVersion) implements Frame {}
	private record Queued(Kind type, String requestId) implements Frame {}
	private record Response(
			@NotNull Kind type,
			@NotNull String id,
			boolean success,
			@Nullable Object result,
			@Nullable String error
	) implements Frame {}
	private record Failure(Kind type, String error) implements Frame {}
	private record Snapshot(Kind type, SessionSnapshot snapshot) implements Frame {}
	private record Output(Kind type, @JsonUnwrapped LogEvent event) implements Frame {}
}
