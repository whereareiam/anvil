package me.whereareiam.anvil.environment.service.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.service.ServiceContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Waits until a started container is ready: until it prints its ready line, or else until each published port
 * reaches a listener inside the container.
 */
final class ServiceReadiness {
	private static final Duration PROBE_INTERVAL = Duration.ofMillis(200);

	private final DockerClient client;
	private final String id;
	private final ServiceContainer declaration;
	private final Map<Integer, InetSocketAddress> published;
	private final Duration timeout;

	ServiceReadiness(
			DockerClient client,
			String id,
			ServiceContainer declaration,
			Map<Integer, InetSocketAddress> published,
			Duration timeout
	) {
		this.client = client;
		this.id = id;
		this.declaration = declaration;
		this.published = published;
		this.timeout = timeout;
	}

	/**
	 * Waits for the container's start from the given moment on.
	 *
	 * @param since when the container was started, so a ready line of an earlier run does not count
	 */
	void await(Instant since) {
		if (declaration.getReadyLog() != null) awaitLog(Pattern.compile(declaration.getReadyLog()), since);
		else awaitPorts(Instant.now().plus(timeout));
	}

	private void awaitLog(Pattern ready, Instant since) {
		CompletableFuture<Void> matched = new CompletableFuture<>();
		StringBuilder pending = new StringBuilder();
		ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
			@Override
			public void onNext(Frame frame) {
				pending.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
				int end;
				while ((end = pending.indexOf("\n")) >= 0) {
					String line = pending.substring(0, end);
					pending.delete(0, end + 1);
					if (ready.matcher(line).find()) matched.complete(null);
				}
			}

			@Override
			public void onComplete() {
				matched.completeExceptionally(new ProvisioningException("The container of service '" + declaration.getName()
						+ "' stopped before printing " + ready.pattern()));
			}
		};

		try (callback) {
			client.logContainerCmd(id)
					.withStdOut(true)
					.withStdErr(true)
					.withFollowStream(true)
					.withSince((int) since.getEpochSecond())
					.exec(callback);
			matched.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException failure) {
			throw new ProvisioningException("Service '" + declaration.getName() + "' did not print " + ready.pattern()
					+ " within " + timeout, failure);
		} catch (ExecutionException failure) {
			if (failure.getCause() instanceof RuntimeException cause) throw cause;
			throw new ProvisioningException("Could not follow the log of service '" + declaration.getName() + "'", failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted waiting for service '" + declaration.getName() + "'", failure);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not close the log of service '" + declaration.getName() + "'", failure);
		}
	}

	private void awaitPorts(Instant deadline) {
		for (Map.Entry<Integer, InetSocketAddress> port : published.entrySet()) {
			while (!reachesListener(port.getValue())) {
				if (Instant.now().isAfter(deadline))
					throw new ProvisioningException("Service '" + declaration.getName() + "' did not listen on port "
							+ port.getKey() + " within " + timeout);

				sleep();
			}
		}
	}

	// Docker accepts a connection to a published port before anything listens inside the container, then closes it
	// right away. A listener keeps it open: the read waits, or the listener greets.
	private static boolean reachesListener(InetSocketAddress address) {
		try (Socket socket = new Socket()) {
			socket.connect(address, (int) PROBE_INTERVAL.toMillis());
			socket.setSoTimeout((int) PROBE_INTERVAL.toMillis());

			return greetsOrWaits(socket.getInputStream());
		} catch (IOException refused) {
			return false;
		}
	}

	private static boolean greetsOrWaits(InputStream input) throws IOException {
		try {
			return input.read() >= 0;
		} catch (SocketTimeoutException open) {
			return true;
		}
	}

	private static void sleep() {
		try {
			Thread.sleep(PROBE_INTERVAL);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted waiting for a service", failure);
		}
	}
}
