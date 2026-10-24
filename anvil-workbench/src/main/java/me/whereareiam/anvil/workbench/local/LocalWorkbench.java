package me.whereareiam.anvil.workbench.local;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.managed.process.PortSelection;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import me.whereareiam.anvil.workbench.local.adapter.execution.CacheImageLocks;
import me.whereareiam.anvil.workbench.local.adapter.execution.JavaExecutionRuntime;
import me.whereareiam.anvil.workbench.requirement.MachineCapacity;
import org.jetbrains.annotations.NotNull;

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
	private final @NotNull Map<String, ExecutionProvider> executions;
	private final @NotNull ProvisioningServices provisioning;
	private final @NotNull JavaExecutionRuntime javaRuntime;
	private final @NotNull CacheImageLocks imageLocks;
	private final @NotNull PortSelection ports = new PortSelection();
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
		this.executions = Map.copyOf(executions);
		provisioning = new ProvisioningServices(options);
		javaRuntime = new JavaExecutionRuntime(provisioning.getJava());
		imageLocks = new CacheImageLocks(provisioning.getCache());
	}

	@Override
	public @NotNull String name() {
		return NAME;
	}

	@Override
	public @NotNull WorkbenchFacts facts() {
		var facts = WorkbenchFacts.builder()
				.workbenchName(NAME)
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
	public @NotNull Map<String, ExecutionProvider> executions() {
		return executions;
	}

	@Override
	public @NotNull ExecutionContext context(@NotNull NetworkPolicy networkPolicy) {
		return ExecutionContext.builder()
				.cacheDirectory(options.getCacheDirectory())
				.localRuntime(javaRuntime)
				.runtimeValidator(javaRuntime)
				.imageLocks(imageLocks)
				.ports(ports)
				.processPriority(options.getProcessScheduling().getPriority())
				.offline(options.isOffline())
				.refresh(options.isRefresh())
				.networkPolicy(networkPolicy)
				.build();
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
