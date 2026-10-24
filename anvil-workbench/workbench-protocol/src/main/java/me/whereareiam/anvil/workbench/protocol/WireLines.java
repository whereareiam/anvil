package me.whereareiam.anvil.workbench.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads and writes the JSON lines of a workbench connection without buffering past a line, so the raw bytes that
 * follow a header stay in the stream.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireLines {
	private static final int MAXIMUM_LINE = 64 * 1024 * 1024;

	/**
	 * Writes one JSON value as a line and flushes it.
	 *
	 * @param mapper mapper of the connection
	 * @param output connection's output
	 * @param message value to write
	 * @throws IOException when the connection fails
	 */
	public static void write(@NotNull ObjectMapper mapper, @NotNull OutputStream output, @NotNull JsonNode message) throws IOException {
		byte[] line = mapper.writeValueAsBytes(message);
		synchronized (output) {
			output.write(line);
			output.write('\n');
			output.flush();
		}
	}

	/**
	 * Reads one JSON line.
	 *
	 * @param mapper mapper of the connection
	 * @param input connection's input
	 * @return the value, or null when the connection ended before a line began
	 * @throws IOException when the connection fails or ends inside a line
	 */
	public static @Nullable JsonNode read(@NotNull ObjectMapper mapper, @NotNull InputStream input) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int next;
		while ((next = input.read()) != '\n') {
			if (next < 0) {
				if (line.size() == 0) return null;
				throw new EOFException("The workbench connection ended inside a message");
			}
			if (line.size() >= MAXIMUM_LINE) throw new IOException("A workbench message exceeds " + MAXIMUM_LINE + " bytes");
			line.write(next);
		}

		return mapper.readTree(line.toString(StandardCharsets.UTF_8));
	}
}
