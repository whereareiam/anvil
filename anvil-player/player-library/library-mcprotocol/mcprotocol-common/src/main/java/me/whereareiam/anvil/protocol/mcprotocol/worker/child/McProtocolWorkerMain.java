package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.jetbrains.annotations.NotNull;

/**
 * Executable composition root for one release-specific MCProtocolLib worker. The release itself, its client
 * segment and the selected capability segments are on the class path chosen by the host.
 */
public final class McProtocolWorkerMain {
	/**
	 * Verifies the segments and the loaded release's protocol before constructing or announcing the worker.
	 *
	 * @param arguments release key version and expected native protocol number
	 * @throws Exception when the loaded runtime does not match or the worker fails
	 */
	public static void main(@NotNull String[] arguments) throws Exception {
		if (arguments.length != 2)
			throw new IllegalArgumentException("Expected the release key version and the native protocol number");

		MinecraftVersion version = MinecraftVersion.parse(arguments[0]);
		int expected = Integer.parseInt(arguments[1]);
		WorkerSegments segments = WorkerSegments.verify(McProtocolWorkerMain.class.getClassLoader(), version, System.err::println);
		McProtocolClient<Object> client = segments.client();
		int actual = client.protocolNumber();
		if (expected != actual) throw new IllegalStateException("Expected protocol " + expected + " but loaded " + actual);

		NativeWorkerContext context = NativeWorkerContext.builder()
				.libraryId(McProtocolClient.LIBRARY_ID)
				.version(version)
				.protocolNumber(actual)
				.nativeSessionType(client.sessionType())
				.build();
		WorkerCapabilityRegistry capabilities = WorkerCapabilityRegistry.discover(context);
		WorkerMessageWriter responses = new WorkerMessageWriter(System.out);
		try (McProtocolWorker worker = new McProtocolWorker(client, segments, capabilities, responses)) {
			responses.ready(version.toString(), actual, segments.selected(), capabilities.capabilities(), capabilities.unavailable());
			worker.run(System.in);
		}
	}
}
