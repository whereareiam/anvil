package me.whereareiam.anvil.environment.service.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Frame;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.service.ServiceContainer;
import me.whereareiam.anvil.api.service.ServiceCommandResult;
import me.whereareiam.anvil.api.type.ServiceState;
import me.whereareiam.anvil.environment.service.api.ServiceInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One service's container, from its first start until the session removes it.
 */
final class DockerService implements ServiceInstance {
	private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

	private final DockerClient client;
	private final String id;
	private final ServiceContainer declaration;
	private final Map<Integer, InetSocketAddress> published;
	private final boolean joined;
	private final ServiceReadiness readiness;
	private volatile boolean starting;

	DockerService(
			DockerClient client,
			String id,
			ServiceContainer declaration,
			Map<Integer, InetSocketAddress> published,
			boolean joined,
			Duration startup
	) {
		this.client = client;
		this.id = id;
		this.declaration = declaration;
		this.published = published;
		this.joined = joined;
		this.readiness = new ServiceReadiness(client, id, declaration, published, startup);
	}

	@Override
	public @NotNull String name() {
		return declaration.getName();
	}

	@Override
	public @NotNull ServiceState state() {
		if (starting) return ServiceState.STARTING;

		InspectContainerResponse.ContainerState state = inspect().getState();
		if (Boolean.TRUE.equals(state.getPaused())) return ServiceState.PAUSED;
		if (Boolean.TRUE.equals(state.getRunning())) return ServiceState.RUNNING;

		return ServiceState.STOPPED;
	}

	@Override
	public @NotNull InetSocketAddress address(int containerPort) {
		InetSocketAddress address = published.get(containerPort);
		if (address == null)
			throw new IllegalArgumentException("Service '" + name() + "' does not listen on port " + containerPort);

		return address;
	}

	@Override
	public @Nullable InetSocketAddress networkAddress(int containerPort) {
		address(containerPort);
		if (!joined) return null;

		return InetSocketAddress.createUnresolved(name(), containerPort);
	}

	void start() {
		Instant since = Instant.now();
		starting = true;
		try {
			client.startContainerCmd(id).exec();
			readiness.await(since);
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Service '" + name() + "' did not start", failure);
		} finally {
			starting = false;
		}
	}

	@Override
	public void pause() {
		if (state() != ServiceState.RUNNING) throw new IllegalStateException("Service '" + name() + "' is not running");

		client.pauseContainerCmd(id).exec();
	}

	@Override
	public void resume() {
		if (state() != ServiceState.PAUSED) throw new IllegalStateException("Service '" + name() + "' is not paused");

		client.unpauseContainerCmd(id).exec();
	}

	@Override
	public void kill() {
		ServiceState state = state();
		if (state == ServiceState.STOPPED) return;
		if (state == ServiceState.PAUSED) client.unpauseContainerCmd(id).exec();

		client.killContainerCmd(id).exec();
	}

	@Override
	public void restart() {
		ServiceState state = state();
		if (state == ServiceState.PAUSED) client.unpauseContainerCmd(id).exec();
		if (state != ServiceState.STOPPED) client.stopContainerCmd(id).withTimeout(10).exec();

		start();
	}

	@Override
	public @NotNull ServiceCommandResult execute(@NotNull String... command) {
		return execute(COMMAND_TIMEOUT, command);
	}

	@Override
	public @NotNull ServiceCommandResult execute(@NotNull Duration timeout, @NotNull String... command) {
		if (state() != ServiceState.RUNNING) throw new IllegalStateException("Service '" + name() + "' is not running");

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
				throw new IllegalStateException("'" + String.join(" ", command) + "' did not finish in service '" + name()
						+ "' within " + timeout);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted running a command in service '" + name() + "'", failure);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not close the command stream of service '" + name() + "'", failure);
		}

		Long exitCode = client.inspectExecCmd(exec).exec().getExitCodeLong();
		return new ServiceCommandResult(exitCode == null ? -1 : exitCode.intValue(), output.toString(StandardCharsets.UTF_8));
	}

	void remove() {
		try {
			client.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();
		} catch (RuntimeException failure) {
			throw new ProvisioningException("Could not remove the container of service '" + name() + "'", failure);
		}
	}

	private InspectContainerResponse inspect() {
		return client.inspectContainerCmd(id).exec();
	}
}
