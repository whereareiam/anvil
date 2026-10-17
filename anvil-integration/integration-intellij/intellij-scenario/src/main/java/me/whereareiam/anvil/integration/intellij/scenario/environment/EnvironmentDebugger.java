package me.whereareiam.anvil.integration.intellij.scenario.environment;

import com.intellij.execution.RunManager;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLogListener;
import org.jetbrains.annotations.NotNull;

/**
 * Attaches the IDE's debugger to each process of a debugged environment once its JVM announces its JDWP port.
 * <p>
 * Each process generation announces a new port, so a restarted process gets a new debug session while the
 * previous one ends with its JVM.
 */
final class EnvironmentDebugger implements SessionLogListener {
	private static final Pattern ANNOUNCEMENT = Pattern.compile("Listening for transport dt_socket at address: (\\d+)");

	private final @NotNull BiConsumer<String, Integer> attach;
	private final Map<String, StringBuilder> lines = new HashMap<>();

	EnvironmentDebugger(@NotNull Project project) {
		this((process, port) -> ApplicationManager.getApplication().invokeLater(() -> attach(project, process, port), project.getDisposed()));
	}

	/**
	 * Creates a debugger that hands each announced port to the given attachment.
	 */
	EnvironmentDebugger(@NotNull BiConsumer<String, Integer> attach) {
		this.attach = attach;
	}

	@Override
	public void cleared() {
		lines.clear();
	}

	@Override
	public void appended(@NotNull SessionLogEntry entry) {
		String process = entry.getProcess();
		if (process == null) return;

		String generation = entry.getExecutionId() == null ? process : entry.getExecutionId().toString();
		StringBuilder pending = lines.computeIfAbsent(generation, ignored -> new StringBuilder()).append(entry.getText());
		for (int end = pending.indexOf("\n"); end >= 0; end = pending.indexOf("\n")) {
			Matcher announcement = ANNOUNCEMENT.matcher(pending.substring(0, end));
			pending.delete(0, end + 1);
			if (announcement.find()) attach.accept(process, Integer.parseInt(announcement.group(1)));
		}
	}

	@Override
	public void finished(int exitCode) {
		lines.clear();
	}

	private static void attach(@NotNull Project project, @NotNull String process, int port) {
		var settings = RunManager.getInstance(project).createConfiguration(process + " (Anvil)", RemoteConfigurationType.getInstance().getConfigurationFactories()[0]);
		var remote = (RemoteConfiguration) settings.getConfiguration();
		remote.HOST = "127.0.0.1";
		remote.PORT = String.valueOf(port);
		remote.SERVER_MODE = false;
		remote.USE_SOCKET_TRANSPORT = true;
		ExecutionUtil.runConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance());
	}
}
