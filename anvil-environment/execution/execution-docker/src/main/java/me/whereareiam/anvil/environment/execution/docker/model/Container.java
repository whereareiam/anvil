package me.whereareiam.anvil.environment.execution.docker.model;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.WaitResponse;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.process.type.RunningContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
	 * Pauses this container: its processes keep their state and sockets but run nothing.
	 */
	public void pause() {
		client.pauseContainerCmd(id).exec();
	}

	/**
	 * Resumes this paused container.
	 */
	public void unpause() {
		client.unpauseContainerCmd(id).exec();
	}

	/**
	 * Runs a command inside this running container and waits for it to finish.
	 *
	 * @param timeout how long to wait
	 * @param command the command and its arguments
	 * @return its exit code and output
	 */
	public RunningContainer.CommandResult execute(Duration timeout, String... command) {
		String exec = client.execCreateCmd(id)
				.withCmd(command)
				.withAttachStdout(true)
				.withAttachStderr(true)
				.exec()
				.getId();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (ResultCallback.Adapter<Frame> callback = client.execStartCmd(exec).exec(new ResultCallback.Adapter<>() {
			@Override
			public void onNext(Frame frame) {
				output.writeBytes(frame.getPayload());
			}
		})) {
			if (!callback.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS))
				throw new IllegalStateException("'" + String.join(" ", command) + "' did not finish within " + timeout);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted running a command in container " + id, failure);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not close the command stream of container " + id, failure);
		}

		Long exitCode = client.inspectExecCmd(exec).exec().getExitCodeLong();
		return new RunningContainer.CommandResult(exitCode == null ? -1 : exitCode.intValue(), output.toString(StandardCharsets.UTF_8));
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
