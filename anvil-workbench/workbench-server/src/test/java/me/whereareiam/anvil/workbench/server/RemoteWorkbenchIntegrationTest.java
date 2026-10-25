package me.whereareiam.anvil.workbench.server;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import me.whereareiam.anvil.workbench.remote.RemoteWorkbench;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import me.whereareiam.anvil.workbench.remote.pairing.Pairing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RemoteWorkbenchIntegrationTest {
	@TempDir
	Path temporary;

	private WorkbenchServer server;
	private Pairing pairing;

	@BeforeEach
	void startWorkbench() throws Exception {
		ServerSettings settings = ServerSettings.read(temporary.resolve("workbench")).toBuilder()
				.name("bench").bindAddress("127.0.0.1").port(0).clearExecutions().execution(ServerSettings.LOCAL).build();
		server = new WorkbenchServer(settings);
		server.start();
		String code = server.newPairingCode();
		pairing = WorkbenchConnection.pair("bench", "127.0.0.1:" + server.port(), code, "test-engine");
	}

	@AfterEach
	void stopWorkbench() throws IOException {
		server.close();
	}

	@Test
	void pairsOnceAndPinsTheWorkbenchsCertificate() throws Exception {
		assertEquals(server.fingerprint(), pairing.getFingerprint());
		assertThrows(IOException.class, () -> WorkbenchConnection.pair("bench", "127.0.0.1:" + server.port(), "WRONGCODE00", "intruder"));

		Pairing forged = pairing.toBuilder().fingerprint("0".repeat(64)).build();
		assertThrows(WorkbenchException.class, () -> RemoteWorkbench.connect(forged));
		Pairing stolen = pairing.toBuilder().token("f".repeat(64)).build();
		assertThrows(WorkbenchException.class, () -> RemoteWorkbench.connect(stolen));
	}

	@Test
	void reportsItsFactsAndRootsAndStoresEngineFilesOnce() throws Exception {
		try (RemoteWorkbench workbench = RemoteWorkbench.connect(pairing)) {
			WorkbenchFacts facts = workbench.facts();
			assertEquals("bench", facts.getWorkbenchName());
			assertTrue(facts.getExecutions().contains("local"));
			assertEquals(temporary.resolve("workbench/work"), workbench.workDirectory());

			Path plugin = Files.writeString(temporary.resolve("plugin.jar"), "plugin content");
			Path stored = workbench.store(plugin);
			assertNotEquals(plugin, stored);
			assertTrue(stored.startsWith(temporary.resolve("workbench/store")));
			assertEquals("plugin content", Files.readString(stored));
			assertEquals(stored, workbench.store(plugin));

			Path data = Files.createDirectories(temporary.resolve("data/nested"));
			Files.writeString(data.resolve("value.txt"), "inside");
			assertEquals("inside", Files.readString(workbench.store(temporary.resolve("data")).resolve("nested/value.txt")));
		}
	}

	@Test
	void readsAndWritesAProcessWorkspaceOnTheWorkbench() throws Exception {
		try (RemoteWorkbench workbench = RemoteWorkbench.connect(pairing)) {
			ProcessFiles files = workbench.workspaces().files(workbench.workDirectory().resolve("run/lobby"));
			files.write("config/server.properties", "motd=Anvil\n");

			assertEquals("motd=Anvil\n", files.read("config/server.properties"));
			assertEquals(java.util.List.of("config"), files.list(""));
			assertThrows(java.nio.file.NoSuchFileException.class, () -> files.read("missing.txt"));
			assertTrue(files.delete("config"));
			assertTrue(files.location().contains("bench"));
		}
	}

	@Test
	void launchesAJvmOnTheWorkbenchAndCarriesItsStreams() throws Exception {
		try (RemoteWorkbench workbench = RemoteWorkbench.connect(pairing)) {
			Process process = workbench.launch(JavaMainLaunch.builder()
					.classpathEntry(workbench.store(testClasses()))
					.mainClass(EchoWorker.class.getName())
					.argument("first")
					.javaVersion(Runtime.version().feature())
					.build());

			OutputStream input = process.getOutputStream();
			input.write("ping\n".getBytes(StandardCharsets.UTF_8));
			input.flush();

			assertEquals("echo:ping:first", new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)).readLine());
			assertEquals("error:ping", new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)).readLine());
			assertEquals(3, process.waitFor());
			assertFalse(process.isAlive());
		}
	}

	@Test
	void runsAProcessThroughAnExecutionSessionAndMirrorsItsPort() throws Exception {
		// The engine and the workbench share this machine, so the mirror takes another loopback address than the
		// process: 127.0.0.2 where the loopback has it, else the IPv6 loopback.
		InetAddress mirror = bindable(InetAddress.getByName("127.0.0.2")) ? InetAddress.getByName("127.0.0.2") : InetAddress.getByName("::1");
		assumeTrue(bindable(mirror), "This machine's loopback offers no second address to mirror ports at");

		try (RemoteWorkbench workbench = RemoteWorkbench.connect(pairing, mirror);
		     ExecutionSession session = workbench.executions().get("local").open(ExecutionContext.builder().build())) {
			ProcessTarget target = session.prepare(ProcessRequest.builder()
					.name("listener")
					.workspace(workbench.workDirectory().resolve("run/listener"))
					.javaSelection(JavaSelection.builder()
							.requirement(JavaRequirement.builder().featureVersion(Runtime.version().feature()).build())
							.source(JavaSource.home(Path.of(System.getProperty("java.home"))))
							.build())
					.build());
			Files.createDirectories(workbench.workDirectory().resolve("run/listener"));
			ProcessExecution execution = target.start(JavaCommand.builder()
					.jar(workbench.store(listeningJar()))
					.memoryMegabytes(512)
					.argument(Integer.toString(target.address().getPort()))
					.build());

			BufferedReader output = new BufferedReader(new InputStreamReader(execution.output(), StandardCharsets.UTF_8));
			String first = output.readLine();
			assertEquals("READY", first, () -> first + "\n" + output.lines().limit(5).toList());
			try (Socket client = new Socket(mirror, target.address().getPort())) {
				assertEquals("hello", new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine());
			}
			assertTrue(execution.await(Duration.ofSeconds(10)));
			execution.onExit().get();
			target.close();
		}
	}

	private Path testClasses() throws URISyntaxException {
		return Path.of(EchoWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private Path listeningJar() throws Exception {
		Path jar = temporary.resolve("listening.jar");
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, ListeningProcess.class.getName());
		String entry = ListeningProcess.class.getName().replace('.', '/') + ".class";
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			output.putNextEntry(new JarEntry(entry));
			Files.copy(testClasses().resolve(entry), output);
			output.closeEntry();
		}
		return jar;
	}

	private static boolean bindable(InetAddress address) {
		try (ServerSocket ignored = new ServerSocket(0, 1, address)) {
			return true;
		} catch (IOException unavailable) {
			return false;
		}
	}
}
