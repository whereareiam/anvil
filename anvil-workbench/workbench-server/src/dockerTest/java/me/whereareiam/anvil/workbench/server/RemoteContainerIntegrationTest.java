package me.whereareiam.anvil.workbench.server;

import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ImageLaunch;
import me.whereareiam.anvil.environment.execution.api.model.process.ContainerImage;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.workbench.remote.RemoteWorkbench;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import me.whereareiam.anvil.workbench.remote.pairing.Pairing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs a container through a workbench whose operator allows only Docker, as a scenario's database would run, and
 * reaches it at its address mirrored on the engine's loopback.
 */
class RemoteContainerIntegrationTest {
	@TempDir
	Path temporary;

	@Test
	void runsAContainerOnTheWorkbenchsDockerAndMirrorsItsPort() throws Exception {
		ServerSettings settings = ServerSettings.read(temporary.resolve("workbench")).toBuilder()
				.name("bench").bindAddress("127.0.0.1").port(0).build();
		try (WorkbenchServer server = new WorkbenchServer(settings)) {
			server.start();
			Pairing pairing = WorkbenchConnection.pair("bench", "127.0.0.1:" + server.port(), server.newPairingCode(), "docker-test");
			// The engine shares this machine with the workbench, so the mirror takes the IPv6 loopback.
			InetAddress mirror = InetAddress.getByName("::1");

			try (RemoteWorkbench workbench = RemoteWorkbench.connect(pairing, ProcessPriority.NORMAL, mirror);
			     ExecutionSession session = workbench.executions().get("docker").open(workbench.context(NetworkPolicy.builder().build()))) {
				assertSame(workbench.executions().get("docker").getClass(), workbench.executions().get("local").getClass(),
						"A Docker-only workbench runs locally declared processes in containers too");
				Path workspace = workbench.workDirectory().resolve("run/redis");
				Files.createDirectories(workspace);
				ProcessTarget target = session.prepare(ProcessRequest.builder()
						.name("redis")
						.workspace(workspace)
						.image(ContainerImage.builder().image(System.getProperty("anvil.docker.containerTestImage")).port(6379).build())
						.build());
				ProcessExecution redis = target.start(ImageLaunch.builder().build());
				try {
					BufferedReader output = new BufferedReader(new InputStreamReader(redis.output(), StandardCharsets.UTF_8));
					String line;
					while ((line = output.readLine()) != null && !line.contains("Ready to accept connections")) {
						// Redis prints its banner first.
					}

					int port = target.address().getPort();
					try (Socket client = new Socket(mirror, port)) {
						OutputStream input = client.getOutputStream();
						input.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
						input.flush();
						assertEquals("+PONG", new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII)).readLine());
					}
				} finally {
					redis.terminate(true);
					redis.await(Duration.ofSeconds(30));
					target.close();
				}
			}
		}
	}
}
