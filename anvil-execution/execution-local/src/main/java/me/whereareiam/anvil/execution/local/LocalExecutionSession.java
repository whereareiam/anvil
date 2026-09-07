package me.whereareiam.anvil.execution.local;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.execution.api.ExecutionSession;
import me.whereareiam.anvil.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.execution.api.model.ProcessRequest;
import me.whereareiam.anvil.execution.local.process.LocalProcessTarget;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Selects host Java and allocates process-local endpoints for one scenario.
 */
@RequiredArgsConstructor
final class LocalExecutionSession implements ExecutionSession {
	private final ExecutionContext context;
	private final LocalExecutionSettings settings;
	private final JavaProvisioner java;
	private final Set<Integer> ports = new HashSet<>();

	@Override
	public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
		if (context.getNetworkPolicy().getNetworkServerAccess() == NetworkServerAccess.PROXY_ONLY
				&& context.getNetworkPolicy().getBackendNetworkExposure() == NetworkExposure.PRIVATE)
			throw new ProvisioningException("Local execution cannot enforce proxy-only private backend access");

		JavaSource source = request.getJavaSource() == null
				? settings.source(request.getJavaRequirement())
				: request.getJavaSource();

		Path executable = java.resolve(request.getJavaRequirement(), request.getMinimumJavaVersion(), source);
		return new LocalProcessTarget(request, executable,
				new InetSocketAddress(context.getBindAddress(), port(context.getBindAddress())),
				new InetSocketAddress("127.0.0.1", port("127.0.0.1")));
	}

	private synchronized int port(String address) {
		try {
			for (int attempt = 0; attempt < 100; attempt++) {
				try (ServerSocket socket = new ServerSocket()) {
					socket.bind(new InetSocketAddress(address, 0));
					if (ports.add(socket.getLocalPort())) return socket.getLocalPort();
				}
			}

			throw new ProvisioningException("Could not allocate distinct local process ports");
		} catch (IOException failure) {
			throw new ProvisioningException("Could not allocate local process port on " + address, failure);
		}
	}

	@Override
	public void close() {
		// All host process generations are owned by their scenario processes.
	}
}
