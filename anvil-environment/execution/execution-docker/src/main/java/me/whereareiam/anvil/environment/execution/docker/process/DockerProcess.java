package me.whereareiam.anvil.environment.execution.docker.process;

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
		if (isAlive()) container.stop(force);
	}
}
