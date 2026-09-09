package me.whereareiam.anvil.environment.execution.docker;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;

/**
 * Owns one Docker network and removes it when the execution session closes.
 */
@RequiredArgsConstructor
public final class DockerNetwork implements AutoCloseable {
	private final DockerClient client;
	private final String id;
	private boolean closed;

	public String id() {
		return id;
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		try {
			client.removeNetworkCmd(id).exec();
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not remove Docker network " + id, failure);
		}
	}
}
