package me.whereareiam.anvil.environment.execution.local;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.environment.execution.api.ExecutionServices;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;

/**
 * Selects host Java and allocates process-local endpoints for one scenario.
 */
@RequiredArgsConstructor
final class LocalExecutionSession implements ExecutionSession {
	/** Starts a process with a lower scheduling priority that its threads and child processes inherit. */
	private static final List<String> LOW_PRIORITY = List.of("nice", "-n", "10");

	private final ExecutionContext context;
	private final ExecutionServices services;
	private final LocalExecutionSettings settings;
	private final LocalRuntimePreparation runtime;
	private final Set<Integer> ports = new HashSet<>();

	@Override
	public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
		if (request.getImage() != null)
			throw new ProvisioningException("Local execution runs no container images; run container '" + request.getName()
					+ "' with an execution provider that does, such as executionProviderId(\"docker\")");
		if (context.getNetworkPolicy().getNetworkServerAccess() == NetworkServerAccess.PROXY_ONLY
				&& context.getNetworkPolicy().getBackendNetworkExposure() == NetworkExposure.PRIVATE)
			throw new ProvisioningException("Local execution cannot enforce proxy-only private backend access");

		JavaSelection java = Objects.requireNonNull(request.getJavaSelection(), "A Java process needs a Java selection");
		JavaSource source = java.getSource() == null ? settings.source(java.getRequirement()) : java.getSource();

		Path executable = runtime.executable(request, source);
		String bindAddress = context.getNetworkPolicy().getBindAddress();
		Map<String, InetSocketAddress> listeners = new LinkedHashMap<>();
		request.getListeners().forEach(listener -> listeners.put(listener, new InetSocketAddress(bindAddress, port(bindAddress))));

		return new LocalProcessTarget(request, executable,
				new InetSocketAddress(bindAddress, port(bindAddress)),
				new InetSocketAddress("127.0.0.1", port("127.0.0.1")),
				Map.copyOf(listeners),
				context.getProcessPriority() == ProcessPriority.LOW ? LOW_PRIORITY : List.of());
	}

	private synchronized int port(String address) {
		int port = services.getPorts().reserve(address);
		ports.add(port);

		return port;
	}

	/**
	 * Releases the ports of this scenario's processes, whose generations their scenario has stopped.
	 */
	@Override
	public synchronized void close() {
		ports.forEach(services.getPorts()::release);
		ports.clear();
	}
}
