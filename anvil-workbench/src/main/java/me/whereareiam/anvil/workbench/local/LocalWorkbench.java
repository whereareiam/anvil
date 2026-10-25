package me.whereareiam.anvil.workbench.local;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionServices;
import me.whereareiam.anvil.environment.execution.managed.process.PortSelection;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import me.whereareiam.anvil.workbench.local.adapter.execution.CacheImageLocks;
import me.whereareiam.anvil.workbench.local.adapter.execution.JavaExecutionRuntime;
import me.whereareiam.anvil.workbench.requirement.MachineCapacity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workbench of the machine the engine runs on, assembled in the engine's own JVM from the environment's
 * blocks: the filesystem cache, artifact, Java and workspace provisioning below the engine's cache directory,
 * and the execution providers the runner installed or supplied.
 *
 * <p>Its processes may contact every host, and its capacity is the machine's.</p>
 */
public final class LocalWorkbench implements Workbench {
	/**
	 * Name of the workbench in the engine's own JVM.
	 */
	public static final String NAME = "local";
	private static final String ANY_HOST = "*";

	private final @NotNull EngineOptions options;
	private final @NotNull ProvisioningServices provisioning;
	private final @NotNull Map<String, BoundExecutionProvider> executions;
	private boolean closed;

	/**
	 * Opens the provisioning services below the engine's cache directory. Failed construction releases every
	 * acquired service.
	 *
	 * @param options resolved engine options
	 * @param executions execution providers by identifier, installed and supplied by the runner
	 */
	public LocalWorkbench(@NotNull EngineOptions options, @NotNull Map<String, ExecutionProvider> executions) {
		this.options = options;
		provisioning = new ProvisioningServices(options);
		JavaExecutionRuntime java = new JavaExecutionRuntime(provisioning.getJava());
		ExecutionServices services = ExecutionServices.builder()
				.cacheDirectory(options.getCacheDirectory())
				.localRuntime(java)
				.runtimeValidator(java)
				.imageLocks(new CacheImageLocks(provisioning.getCache()))
				.ports(new PortSelection())
				.build();
		Map<String, BoundExecutionProvider> bound = new LinkedHashMap<>();
		executions.forEach((id, provider) -> bound.put(id, new ServicedExecutionProvider(provider, services)));
		this.executions = Collections.unmodifiableMap(bound);
	}

	@Override
	public @NotNull String name() {
		return NAME;
	}

	@Override
	public @NotNull WorkbenchFacts facts() {
		var facts = WorkbenchFacts.builder()
				.workbenchName(NAME)
				.local(true)
				.processors(MachineCapacity.processors())
				.memoryMegabytes(MachineCapacity.memoryMegabytes())
				.executions(executions.keySet())
				.reachableHost(ANY_HOST);
		executions.forEach((id, provider) -> {
			String reason = provider.unavailableReason();
			if (reason != null) facts.unavailableExecution(id, reason);
		});

		return facts.build();
	}

	@Override
	public @NotNull Map<String, BoundExecutionProvider> executions() {
		return executions;
	}

	@Override
	public @NotNull Path workDirectory() {
		return options.getWorkDirectory();
	}

	@Override
	public @NotNull Path cacheDirectory() {
		return options.getCacheDirectory();
	}

	/**
	 * The engine's machine is this workbench's, so a file stays where it is.
	 */
	@Override
	public @NotNull Path store(@NotNull Path file) {
		return file.toAbsolutePath().normalize();
	}

	/**
	 * The engine's loopback is this workbench's, so the service is reachable already.
	 */
	@Override
	public void expose(int port) {
	}

	/**
	 * Starts the JVM with the engine's own Java runtime, which must be recent enough.
	 */
	@Override
	public @NotNull Process launch(@NotNull JavaMainLaunch launch) throws IOException {
		int java = Runtime.version().feature();
		if (java < launch.getJavaVersion())
			throw new IllegalStateException(launch.getMainClass() + " requires Java " + launch.getJavaVersion()
					+ ", but the local workbench runs Java " + java);

		String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
		List<String> command = new ArrayList<>();
		command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
		command.addAll(launch.getJvmArguments());
		command.add("-cp");
		command.add(String.join(File.pathSeparator, launch.getClasspath().stream().map(Path::toString).toList()));
		command.add(launch.getMainClass());
		command.addAll(launch.getArguments());

		return new ProcessBuilder(command).start();
	}

	@Override
	public @NotNull ArtifactResolver artifacts() {
		return provisioning.getArtifacts();
	}

	@Override
	public @NotNull JavaProvisioner java() {
		return provisioning.getJava();
	}

	@Override
	public @NotNull WorkspaceProvisioner workspaces() {
		return provisioning.getWorkspaces();
	}

	/**
	 * Closes the download pool. Repeated calls have no effect.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		provisioning.close();
	}
}
