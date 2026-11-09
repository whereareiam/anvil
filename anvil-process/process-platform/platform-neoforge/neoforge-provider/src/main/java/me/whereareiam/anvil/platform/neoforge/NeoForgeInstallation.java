package me.whereareiam.anvil.platform.neoforge;

import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * One NeoForge release in the cache: its installer and the server the installer produces. The server is installed
 * through {@link PlatformArtifactSource#install}, which runs the installer once for scenarios that prepare the
 * release at once and publishes the server only when it is complete.
 */
final class NeoForgeInstallation {
	private static final String SERVER_JAR = "server.jar";
	private static final Set<String> INSTALLER_OUTPUT = Set.of(SERVER_JAR, "installer.jar.log");
	private static final long TIMEOUT_MINUTES = 15;

	private final Path directory;
	private final Path server;

	NeoForgeInstallation(@NotNull Path directory) {
		this.directory = directory;
		this.server = directory.resolve("server");
	}

	@NotNull Path directory() {
		return directory;
	}

	/**
	 * The starter JAR that launches the installed server from a workspace holding the linked server files.
	 */
	@NotNull Path serverJar() {
		return server.resolve(SERVER_JAR);
	}

	/**
	 * Installs the server unless it is installed. The installer downloads the Minecraft server and NeoForge's
	 * libraries itself.
	 */
	void install(@NotNull Path installer, @NotNull PlatformArtifactSource artifacts) {
		artifacts.install(server, staging -> {
			run(installer, staging);
			if (!Files.isRegularFile(staging.resolve(SERVER_JAR)))
				throw new PlatformException("NeoForge installer produced no " + SERVER_JAR + ": " + installer);
		});
	}

	/**
	 * Links the installed server files, such as {@code libraries} and the run scripts the starter JAR reads,
	 * into a process workspace. Files that already exist are kept, so a restart links nothing again.
	 */
	void linkInto(@NotNull Path workspace) throws IOException {
		if (!Files.isDirectory(server))
			throw new PlatformException("NeoForge server is not installed: " + server);

		List<Path> files;
		try (Stream<Path> tree = Files.walk(server)) {
			files = tree.filter(Files::isRegularFile)
					.filter(file -> !INSTALLER_OUTPUT.contains(server.relativize(file).toString()))
					.toList();
		}

		for (Path file : files) {
			Path target = workspace.resolve(server.relativize(file).toString());
			if (Files.exists(target))
				continue;

			Files.createDirectories(target.getParent());
			link(file, target);
		}
	}

	private void run(Path installer, Path staging) throws IOException {
		Path log = directory.resolve("install.log");
		Process process = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-jar", installer.toString(),
				"--install-server", staging.toString(),
				"--server-starter"
		).directory(staging.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();

		try {
			if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new PlatformException("NeoForge installer did not finish within " + TIMEOUT_MINUTES
						+ " minutes; see " + log);
			}
		} catch (InterruptedException interrupted) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new PlatformException("Interrupted while installing NeoForge", interrupted);
		}

		if (process.exitValue() != 0)
			throw new PlatformException("NeoForge installer failed with exit code " + process.exitValue() + "; see " + log);
	}

	private void link(Path file, Path target) throws IOException {
		try {
			Files.createLink(target, file);
		} catch (UnsupportedOperationException | IOException unsupported) {
			Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}
}
