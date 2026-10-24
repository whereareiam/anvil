package me.whereareiam.anvil.workbench.server;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.workbench.remote.WorkbenchCommands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Entry point of the workbench JAR and image. On the workbench's machine it serves engines ({@code run}, the
 * default) and creates pairing codes ({@code pair}); on an engine's machine the same JAR manages the engine's
 * pairings ({@code add}, {@code list}, {@code default}, {@code remove}).
 *
 * <pre>{@code
 * java -jar anvil-workbench.jar run --data /srv/anvil
 * java -jar anvil-workbench.jar pair --data /srv/anvil
 * java -jar anvil-workbench.jar add workstation 192.168.1.20:7400 4F2A9C01D3
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkbenchServerMain {
	private static final String DATA_ENVIRONMENT = "ANVIL_WORKBENCH_DATA";

	/**
	 * Runs one command.
	 *
	 * @param arguments command and its arguments
	 * @throws IOException when the workbench cannot start
	 */
	public static void main(String[] arguments) throws IOException {
		String command = arguments.length == 0 ? "run" : arguments[0];
		List<String> rest = arguments.length == 0 ? List.of() : Arrays.asList(arguments).subList(1, arguments.length);
		switch (command) {
			case "run" -> run(settings(rest));
			case "pair" -> pair(settings(rest));
			case "add", "list", "default", "remove" -> WorkbenchCommands.main(arguments);
			default -> {
				System.err.println("Usage: run | pair [--data DIR] | add NAME HOST:PORT CODE | list | default NAME | remove NAME");
				System.exit(2);
			}
		}
	}

	private static void run(ServerSettings settings) throws IOException {
		WorkbenchServer server = new WorkbenchServer(settings);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				server.close();
			} catch (IOException ignored) {
				// The process is ending.
			}
		}));

		System.out.println("Anvil workbench '" + settings.getName() + "' listens on port " + server.port()
				+ " with certificate " + server.fingerprint());
		System.out.println("It runs processes with " + String.join(", ", settings.getExecutions()) + "; data in " + settings.getDataDirectory());
		if (!server.paired()) printPairing(settings, server.newPairingCode(), server.port());
		server.serve();
	}

	private static void pair(ServerSettings settings) throws IOException {
		try (WorkbenchServer server = new WorkbenchServer(settings.toBuilder().port(0).bindAddress("127.0.0.1").build())) {
			printPairing(settings, server.newPairingCode(), settings.getPort());
		}
	}

	private static void printPairing(ServerSettings settings, String code, int port) {
		System.out.println();
		System.out.println("Pair an engine within 15 minutes, on the engine's machine:");
		System.out.println("  java -jar anvil-workbench.jar add " + settings.getName() + " <this-host>:" + port + " " + code);
		System.out.println();
	}

	private static ServerSettings settings(List<String> arguments) throws IOException {
		String data = System.getenv(DATA_ENVIRONMENT);
		Integer port = null;
		for (int index = 0; index < arguments.size(); index++) {
			String argument = arguments.get(index);
			if (argument.equals("--data") && index + 1 < arguments.size()) data = arguments.get(++index);
			else if (argument.equals("--port") && index + 1 < arguments.size()) port = Integer.parseInt(arguments.get(++index));
		}

		Path directory = data == null ? Path.of(System.getProperty("user.home"), ".anvil-workbench") : Path.of(data);
		ServerSettings settings = ServerSettings.read(directory);
		return port == null ? settings : settings.toBuilder().port(port).build();
	}
}
