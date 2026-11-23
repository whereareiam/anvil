package me.whereareiam.anvil.player.library.mcprotocol.worker.host;

import me.whereareiam.anvil.player.api.library.ProtocolWorkerLauncher;
import me.whereareiam.anvil.player.api.model.WorkerLaunch;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts workers with the test JVM's own Java runtime, as the local workbench does.
 */
final class LocalWorkerLauncher implements ProtocolWorkerLauncher {
	@Override
	public @NotNull Process launch(@NotNull WorkerLaunch launch) throws IOException {
		List<Path> classpath = new ArrayList<>(launch.closure());
		classpath.addAll(launch.engineEntries());

		List<String> command = new ArrayList<>();
		command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		command.addAll(launch.jvmArguments());
		command.add("-cp");
		command.add(String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()));
		command.add(launch.mainClass());
		command.addAll(launch.arguments());

		return new ProcessBuilder(command).start();
	}
}
