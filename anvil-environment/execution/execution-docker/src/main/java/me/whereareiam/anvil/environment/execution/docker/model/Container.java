package me.whereareiam.anvil.environment.execution.docker.model;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.WaitResponse;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;

import java.util.concurrent.TimeUnit;

/**
 * Owns one Docker container and exposes its process lifecycle to Anvil.
 */
@RequiredArgsConstructor
public final class Container {
	private final DockerClient client;
	private final String id;

	/** Returns the Docker identifier for this container. */
	public String id() {
		return id;
	}

	/**
	 * Stops the container, escalating to SIGKILL when requested.
	 */
	public void stop(boolean force) {
		if (force) {
			client.killContainerCmd(id)
					.withSignal("KILL")
					.exec();
			return;
		}

		client.stopContainerCmd(id)
				.withTimeout(15)
				.exec();
	}

	/**
	 * Removes this container forcibly.
	 */
	public void remove() {
		client.removeContainerCmd(id).withForce(true).exec();
	}

	/**
	 * Starts this container.
	 */
	public void start() {
		client.startContainerCmd(id).exec();
	}

	/**
	 * Waits until this container exits or the bounded wait expires.
	 */
	public void awaitExit() {
		try {
			if (!client.waitContainerCmd(id)
					.exec(new ResultCallback.Adapter<WaitResponse>() {})
					.awaitCompletion(5, TimeUnit.MINUTES))
				throw new ProvisioningException("Timed out waiting for Docker container " + id);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted waiting for Docker container " + id, failure);
		} catch (ProvisioningException failure) {
			throw failure;
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not wait for Docker container " + id, failure);
		}
	}

}
