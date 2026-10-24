package me.whereareiam.anvil.workbench.server;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies bytes between the two ends of a relayed connection, each direction on its own virtual thread.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StreamPipes {
	/**
	 * Copies until the input ends, then closes what the copy owns.
	 *
	 * @param name thread name
	 * @param input source
	 * @param output destination
	 * @param onEnd closed once the input ended or failed
	 */
	static void copy(@NotNull String name, @NotNull InputStream input, @NotNull OutputStream output, @NotNull Closeable onEnd) {
		Thread.ofVirtual().name(name).start(() -> {
			try (onEnd) {
				byte[] buffer = new byte[16 * 1024];
				int read;
				while ((read = input.read(buffer)) >= 0) {
					output.write(buffer, 0, read);
					output.flush();
				}
			} catch (IOException ignored) {
				// Either end went away; closing the other ends the relay.
			}
		});
	}
}
