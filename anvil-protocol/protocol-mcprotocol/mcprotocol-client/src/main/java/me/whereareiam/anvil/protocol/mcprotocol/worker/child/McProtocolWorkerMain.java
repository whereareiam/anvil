package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerBinding;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;

/**
 * Executable composition root for one version-specific MCProtocolLib worker.
 */
public final class McProtocolWorkerMain {
	/**
	 * Validates the native codec before constructing or announcing the worker.
	 */
	public static void main(@NotNull String[] arguments) throws Exception {
		if (arguments.length != 2)
			throw new IllegalArgumentException("Expected a native protocol number and binding family");

		int expected = Integer.parseInt(arguments[0]);
		var bindings = ServiceLoader.load(ProtocolWorkerBinding.class).stream().map(ServiceLoader.Provider::get)
				.filter(binding -> binding.family().equals(arguments[1])).toList();
		if (bindings.size() != 1)
			throw new IllegalStateException("Expected one native binding for " + arguments[1] + ", found " + bindings.size());
		ProtocolWorkerBinding binding = bindings.getFirst();
		int actual = binding.protocolNumber();
		if (expected != actual) throw new IllegalStateException("Expected protocol " + expected + " but loaded " + actual);

		WorkerCapabilityRegistry capabilities = WorkerCapabilityRegistry.discover(actual);
		WorkerMessageWriter responses = new WorkerMessageWriter(System.out);
		try (McProtocolWorker worker = new McProtocolWorker(capabilities, responses, binding)) {
			responses.ready(actual, capabilities.capabilities());
			worker.run(System.in);
		}
	}
}
