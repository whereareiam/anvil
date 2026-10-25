package me.whereareiam.anvil.workbench.protocol;

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

/**
 * Reads and writes the messages of a workbench connection, one JSON value per line, without buffering past a line,
 * so the raw bytes that follow a header stay in the stream.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireLines {
	private static final int MAXIMUM_LINE = 64 * 1024 * 1024;

	/**
	 * Writes one message as a line and flushes it.
	 *
	 * @param mapper mapper of the connection
	 * @param output connection's output
	 * @param message message to write
	 * @throws IOException when the connection fails
	 */
	public static void write(@NotNull ObjectMapper mapper, @NotNull OutputStream output, @NotNull Object message) throws IOException {
		byte[] line = mapper.writeValueAsBytes(message);
		synchronized (output) {
			output.write(line);
			output.write('\n');
			output.flush();
		}
	}

	/**
	 * Reads one message.
	 *
	 * @param mapper mapper of the connection
	 * @param input connection's input
	 * @param type type of the message
	 * @param <T> type of the message
	 * @return the message, or null when the connection ended before a line began
	 * @throws IOException when the connection fails, ends inside a line, or carries no such message
	 */
	public static <T> @Nullable T read(@NotNull ObjectMapper mapper, @NotNull InputStream input, @NotNull Class<T> type) throws IOException {
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

		return mapper.readValue(line.toByteArray(), type);
	}
}
