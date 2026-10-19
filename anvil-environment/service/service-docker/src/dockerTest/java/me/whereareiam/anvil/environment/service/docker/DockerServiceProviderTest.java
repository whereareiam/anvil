package me.whereareiam.anvil.environment.service.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.service.ServiceContainer;
import me.whereareiam.anvil.api.type.ServiceState;
import me.whereareiam.anvil.environment.service.api.HostPorts;
import me.whereareiam.anvil.environment.service.api.ServiceInstance;
import me.whereareiam.anvil.environment.service.api.ServiceSession;
import me.whereareiam.anvil.environment.service.api.model.ServiceContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DockerServiceProviderTest {
	private static final ServiceContainer REDIS = ServiceContainer.builder()
			.name("redis")
			.image(System.getProperty("anvil.docker.serviceTestImage", "redis:7.2-alpine"))
			.port(6379)
			.readyLog("Ready to accept connections")
			.build();

	private static DockerClient docker;

	private final Ports ports = new Ports();

	@BeforeAll
	static void connect() {
		DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		docker = DockerClientImpl.getInstance(config, new ZerodepDockerHttpClient.Builder()
				.dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig())
				.build());
	}

	@AfterAll
	static void disconnect() throws IOException {
		docker.close();
	}

	@Test
	void startsAServiceTheTestReachesAndRunsCommandsIn() throws IOException {
		try (ServiceSession session = open(null)) {
			ServiceInstance redis = session.start(REDIS);

			assertEquals(ServiceState.RUNNING, redis.state());
			assertEquals("+PONG", ping(redis.address(6379)));
			assertNull(redis.networkAddress(6379));
			var result = redis.execute("redis-cli", "PING");
			assertEquals(0, result.exitCode());
			assertEquals("PONG", result.output().trim());
			assertThrows(IllegalArgumentException.class, () -> redis.address(6380));
		}

		assertTrue(ports.reserved.isEmpty());
	}

	@Test
	void becomesReadyWhenItsPortReachesAListenerWithoutAReadyLog() throws IOException {
		try (ServiceSession session = open(null)) {
			ServiceInstance redis = session.start(REDIS.toBuilder().readyLog(null).build());

			assertEquals("+PONG", ping(redis.address(6379)));
		}
	}

	@Test
	void aPausedServiceAnswersNobodyUntilResumedAndKeepsItsData() throws IOException {
		try (ServiceSession session = open(null)) {
			ServiceInstance redis = session.start(REDIS);
			assertEquals(0, redis.execute("redis-cli", "SET", "key", "kept").exitCode());

			redis.pause();
			assertEquals(ServiceState.PAUSED, redis.state());
			assertThrows(SocketTimeoutException.class, () -> ping(redis.address(6379)));
			assertThrows(IllegalStateException.class, redis::pause);

			redis.resume();
			assertEquals(ServiceState.RUNNING, redis.state());
			assertEquals("kept", redis.execute("redis-cli", "GET", "key").output().trim());
		}
	}

	@Test
	void aKilledServiceRestartsAtTheSameAddress() throws IOException {
		try (ServiceSession session = open(null)) {
			ServiceInstance redis = session.start(REDIS);
			InetSocketAddress address = redis.address(6379);

			redis.kill();
			assertEquals(ServiceState.STOPPED, redis.state());
			assertThrows(IllegalStateException.class, () -> redis.execute("redis-cli", "PING"));

			redis.restart();
			assertEquals(ServiceState.RUNNING, redis.state());
			assertEquals(address, redis.address(6379));
			assertEquals("+PONG", ping(address));
		}
	}

	@Test
	void joinsTheProcessesNetworkUnderItsName() {
		String network = docker.createNetworkCmd().withName("anvil-service-test-" + System.nanoTime())
				.withLabels(Map.of("me.whereareiam.anvil", "true")).exec().getId();
		try (ServiceSession session = open(network)) {
			ServiceInstance redis = session.start(REDIS);

			assertEquals(InetSocketAddress.createUnresolved("redis", 6379), redis.networkAddress(6379));
			String container = docker.listContainersCmd().withNetworkFilter(Set.of(network)).exec().getFirst().getId();
			var attached = docker.inspectContainerCmd(container).exec().getNetworkSettings().getNetworks().values().stream()
					.filter(settings -> network.equals(settings.getNetworkID()))
					.findFirst()
					.orElseThrow();
			assertTrue(attached.getAliases().contains("redis"));
		} finally {
			docker.removeNetworkCmd(network).exec();
		}
	}

	@Test
	void aServiceThatNeverBecomesReadyFailsWithItsName() {
		try (ServiceSession session = open(null, Duration.ofSeconds(3))) {
			var failure = assertThrows(ProvisioningException.class, () -> session.start(REDIS.toBuilder().readyLog("never printed").build()));
			assertTrue(failure.getMessage().contains("'redis'"), failure.getMessage());
		}
	}

	private ServiceSession open(String network) {
		return open(network, Duration.ofMinutes(2));
	}

	private ServiceSession open(String network, Duration startup) {
		return new DockerServiceProvider().open(ServiceContext.builder()
				.ports(ports)
				.network(network)
				.startup(startup)
				.build());
	}

	private static String ping(InetSocketAddress address) throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(address, 2000);
			socket.setSoTimeout(2000);
			OutputStream output = socket.getOutputStream();
			output.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
			output.flush();

			InputStream input = socket.getInputStream();
			StringBuilder line = new StringBuilder();
			int read;
			while ((read = input.read()) >= 0 && read != '\r') line.append((char) read);

			return line.toString();
		}
	}

	private static final class Ports implements HostPorts {
		private final Set<Integer> reserved = new HashSet<>();

		@Override
		public synchronized int reserve(@NotNull String bindAddress) {
			try (ServerSocket socket = new ServerSocket(0)) {
				reserved.add(socket.getLocalPort());
				return socket.getLocalPort();
			} catch (IOException failure) {
				throw new IllegalStateException(failure);
			}
		}

		@Override
		public synchronized void release(int port) {
			reserved.remove(port);
		}
	}
}
