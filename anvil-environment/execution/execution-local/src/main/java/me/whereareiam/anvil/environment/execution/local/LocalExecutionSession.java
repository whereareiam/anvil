package me.whereareiam.anvil.environment.execution.local;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.api.runtime.LocalRuntimePreparation;
import me.whereareiam.anvil.environment.execution.local.process.LocalProcessTarget;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Selects host Java and allocates process-local endpoints for one scenario.
 */
@RequiredArgsConstructor
final class LocalExecutionSession implements ExecutionSession {
	/** Starts a process with a lower scheduling priority that its threads and child processes inherit. */
	private static final List<String> LOW_PRIORITY = List.of("nice", "-n", "10");

	private final ExecutionContext context;
	private final LocalExecutionSettings settings;
	private final LocalRuntimePreparation runtime;
	private final Set<Integer> ports = new HashSet<>();

	@Override
	public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
		if (context.getNetworkPolicy().getNetworkServerAccess() == NetworkServerAccess.PROXY_ONLY
				&& context.getNetworkPolicy().getBackendNetworkExposure() == NetworkExposure.PRIVATE)
			throw new ProvisioningException("Local execution cannot enforce proxy-only private backend access");

		JavaSource source = request.getJavaSelection().getSource() == null
				? settings.source(request.getJavaSelection().getRequirement())
				: request.getJavaSelection().getSource();

		Path executable = runtime.executable(request, source);
		return new LocalProcessTarget(request, executable,
				new InetSocketAddress(context.getNetworkPolicy().getBindAddress(), port(context.getNetworkPolicy().getBindAddress())),
				new InetSocketAddress("127.0.0.1", port("127.0.0.1")),
				context.getProcessPriority() == ProcessPriority.LOW ? LOW_PRIORITY : List.of());
	}

	private synchronized int port(String address) {
		int port = context.getPorts().reserve(address);
		ports.add(port);

		return port;
	}

	/**
	 * Releases the ports of this scenario's processes, whose generations their scenario has stopped.
	 */
	@Override
	public synchronized void close() {
		ports.forEach(context.getPorts()::release);
		ports.clear();
	}
}
