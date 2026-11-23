package me.whereareiam.anvil.launcher.adapter.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.player.api.library.ProtocolWorkerLauncher;
import me.whereareiam.anvil.player.api.model.WorkerLaunch;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Starts a protocol library's worker JVM on the workbench, handing it the entries of the engine's class path the
 * worker needs.
 */
@RequiredArgsConstructor
public final class WorkbenchWorkerLauncher implements ProtocolWorkerLauncher {
	private final @NotNull Workbench workbench;

	@Override
	public @NotNull Process launch(@NotNull WorkerLaunch launch) throws IOException {
		var java = JavaMainLaunch.builder()
				.classpath(launch.closure())
				.mainClass(launch.mainClass())
				.jvmArguments(launch.jvmArguments())
				.arguments(launch.arguments())
				.javaVersion(launch.javaVersion());
		launch.engineEntries().forEach(entry -> java.classpathEntry(workbench.store(entry)));

		return workbench.launch(java.build());
	}
}
