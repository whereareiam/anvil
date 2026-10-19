package me.whereareiam.anvil.environment.execution.docker.process;

import me.whereareiam.anvil.api.process.type.RunningContainer;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.docker.DockerContainerFactory;
import me.whereareiam.anvil.environment.execution.docker.container.ContainerAttachment;
import me.whereareiam.anvil.environment.execution.docker.model.Container;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Couples console attachment to the actual container lifecycle; killing the CLI never substitutes
 * for stopping a container.
 */
final class DockerProcess implements ProcessExecution {
	private final Container container;
	private final ContainerAttachment attachment;
	private volatile boolean frozen;

	DockerProcess(DockerContainerFactory containers, Container container) {
		this.container = container;
		this.attachment = containers.attach(container);
	}

	@Override
	public @NotNull InputStream output() {
		return attachment.output();
	}

	@Override
	public @NotNull OutputStream input() {
		return attachment.input();
	}

	@Override
	public boolean isAlive() {
		return !attachment.exited().isDone();
	}

	@Override
	public @NotNull CompletableFuture<Void> onExit() {
		return attachment.exited().thenApply(ignored -> null);
	}

	@Override
	public boolean await(@NotNull Duration timeout) throws InterruptedException {
		try {
			attachment.exited().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			return true;
		} catch (TimeoutException failure) {
			return false;
		} catch (ExecutionException failure) {
			return true;
		}
	}

	@Override
	public void terminate(boolean force) {
		// Docker refuses to stop a paused container.
		thaw();
		if (isAlive()) container.stop(force);
	}

	@Override
	public @NotNull RunningContainer.CommandResult execute(@NotNull Duration timeout, @NotNull String... command) {
		if (!isAlive()) throw new IllegalStateException("The container is not running");
		if (frozen) throw new IllegalStateException("The container is frozen; thaw it to run a command in it");

		return container.execute(timeout, command);
	}

	@Override
	public synchronized void freeze() {
		if (frozen || !isAlive()) return;

		container.pause();
		frozen = true;
	}

	@Override
	public synchronized void thaw() {
		if (!frozen) return;

		container.unpause();
		frozen = false;
	}
}
