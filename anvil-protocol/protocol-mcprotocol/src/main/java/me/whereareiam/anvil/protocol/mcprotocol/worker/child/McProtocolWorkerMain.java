package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Executable composition root for one version-specific MCProtocolLib worker.
 */
public final class McProtocolWorkerMain {
	/**
	 * Validates the native codec before constructing or announcing the worker.
	 */
	public static void main(@NotNull String[] arguments) throws Exception {
		if (arguments.length != 1) throw new IllegalArgumentException("Expected exactly one native protocol number");

		int expected = Integer.parseInt(arguments[0]);
		int actual = MinecraftCodec.CODEC.getProtocolVersion();
		if (expected != actual) throw new IllegalStateException("Expected protocol " + expected + " but loaded " + actual);

		WorkerCapabilityRegistry capabilities = WorkerCapabilityRegistry.discover(actual);
		WorkerMessageWriter responses = new WorkerMessageWriter(System.out);
		try (McProtocolWorker worker = new McProtocolWorker(capabilities, responses)) {
			responses.ready(actual, capabilities.capabilities());
			worker.run(System.in);
		}
	}
}
