package me.whereareiam.anvil.launcher.adapter.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.library.ProtocolWorkerLauncher;
import me.whereareiam.anvil.protocol.api.model.WorkerLaunch;
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
				.classpath(launch.getClosure())
				.mainClass(launch.getMainClass())
				.jvmArguments(launch.getJvmArguments())
				.arguments(launch.getArguments())
				.javaVersion(launch.getJavaVersion());
		launch.getEngineEntries().forEach(entry -> java.classpathEntry(workbench.store(entry)));

		return workbench.launch(java.build());
	}
}
