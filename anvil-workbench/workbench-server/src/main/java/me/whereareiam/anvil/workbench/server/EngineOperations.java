package me.whereareiam.anvil.workbench.server;

import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import me.whereareiam.anvil.workbench.protocol.model.Handle;
import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ArtifactRequest;
import me.whereareiam.anvil.workbench.protocol.model.execution.ExecutionRequest;
import me.whereareiam.anvil.workbench.protocol.model.file.FileRequest;
import me.whereareiam.anvil.workbench.protocol.model.java.JavaRequest;
import me.whereareiam.anvil.workbench.protocol.model.launch.AwaitLaunched;
import me.whereareiam.anvil.workbench.protocol.model.launch.DestroyLaunched;
import me.whereareiam.anvil.workbench.protocol.model.launch.Launch;
import me.whereareiam.anvil.workbench.protocol.model.launch.LaunchRequest;
import me.whereareiam.anvil.workbench.protocol.model.launch.LaunchedExit;
import me.whereareiam.anvil.workbench.protocol.model.store.FindStored;
import me.whereareiam.anvil.workbench.protocol.model.store.StoreRequest;
import me.whereareiam.anvil.workbench.protocol.model.store.StoredFile;
import me.whereareiam.anvil.workbench.protocol.model.workbench.Expose;
import me.whereareiam.anvil.workbench.protocol.model.workbench.Greeting;
import me.whereareiam.anvil.workbench.protocol.model.workbench.Hello;
import me.whereareiam.anvil.workbench.protocol.model.workbench.OfferedExecution;
import me.whereareiam.anvil.workbench.protocol.model.workbench.ReadFacts;
import me.whereareiam.anvil.workbench.protocol.model.workbench.WorkbenchSetupRequest;
import me.whereareiam.anvil.workbench.protocol.model.workspace.WorkspaceProvisioningRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Carries out one engine's requests on the workbench, each subject exhaustively: the compiler reports a request a
 * subject does not answer. Objects that outlive one request are held as handles for the engine.
 */
final class EngineOperations {
	private final @NotNull Workbench workbench;
	private final @NotNull ServerSettings settings;
	private final @NotNull Handles handles;
	private final @NotNull UploadStore uploads;
	private final @NotNull String owner;
	private final @NotNull Exposures exposures;
	private final @NotNull ProvisioningOperations provisioning;
	private final @NotNull ExecutionOperations executions;

	EngineOperations(
			@NotNull Workbench workbench,
			@NotNull ServerSettings settings,
			@NotNull Handles handles,
			@NotNull UploadStore uploads,
			@NotNull String owner,
			@NotNull Exposures exposures
	) {
		this.workbench = workbench;
		this.settings = settings;
		this.handles = handles;
		this.uploads = uploads;
		this.owner = owner;
		this.exposures = exposures;
		this.provisioning = new ProvisioningOperations(workbench, handles, owner);
		this.executions = new ExecutionOperations(workbench, handles, owner);
	}

	/**
	 * Carries out one request.
	 *
	 * @param request the engine's request
	 * @return its answer, of the request's answer type
	 * @throws Exception the failure the engine receives
	 */
	@NotNull Object handle(@NotNull WorkbenchRequest<?> request) throws Exception {
		return switch (request) {
			case WorkbenchSetupRequest<?> setup -> setup(setup);
			case StoreRequest<?> store -> store(store);
			case LaunchRequest<?> launch -> launch(launch);
			case ArtifactRequest<?> artifact -> provisioning.artifact(artifact);
			case JavaRequest<?> java -> provisioning.java(java);
			case WorkspaceProvisioningRequest<?> workspace -> provisioning.workspace(workspace);
			case FileRequest<?> file -> provisioning.file(file);
			case ExecutionRequest<?> execution -> executions.handle(execution);
			default -> throw new UnsupportedOperationException("The workbench answers no " + request.getClass().getSimpleName());
		};
	}

	private Object setup(WorkbenchSetupRequest<?> request) throws Exception {
		return switch (request) {
			case Hello hello -> greet(hello);
			case ReadFacts ignored -> settings.restrict(workbench.facts());
			case Expose expose -> {
				exposures.expose(expose.getPort());
				yield new Done();
			}
		};
	}

	private Greeting greet(Hello hello) {
		if (hello.getVersion() != WireProtocol.VERSION)
			throw new IllegalStateException("This workbench speaks protocol " + WireProtocol.VERSION + ", but the engine speaks "
					+ hello.getVersion() + "; run the same Anvil version on both");

		return new Greeting(settings.getName(), workbench.workDirectory(), workbench.cacheDirectory(),
				workbench.executions().entrySet().stream().map(entry -> offered(entry.getKey(), entry.getValue())).toList());
	}

	/**
	 * Offers a provider under the name the workbench offers it by, which differs from its own where the policy lets
	 * Docker run what scenarios run locally.
	 */
	private static OfferedExecution offered(String name, BoundExecutionProvider provider) {
		return new OfferedExecution(name, provider.preservesClientAddress(), provider.connectsEnvironments());
	}

	private Object store(StoreRequest<?> request) {
		return switch (request) {
			case FindStored find -> new StoredFile(uploads.find(find.getSha256(), find.getName()));
		};
	}

	private Object launch(LaunchRequest<?> request) throws Exception {
		return switch (request) {
			case Launch launch -> {
				Process process = workbench.launch(launch.getLaunch());
				yield new Handle(handles.register(owner, process, process::destroyForcibly));
			}
			case AwaitLaunched await -> {
				Process process = handles.get(owner, await.getHandle(), Process.class);
				if (await.getTimeoutMillis() < 0) yield new LaunchedExit(process.waitFor());
				yield new LaunchedExit(process.waitFor(await.getTimeoutMillis(), TimeUnit.MILLISECONDS) ? process.exitValue() : null);
			}
			case DestroyLaunched destroy -> {
				Process process = handles.get(owner, destroy.getHandle(), Process.class);
				if (destroy.isForce()) process.destroyForcibly();
				else process.destroy();
				yield new Done();
			}
		};
	}
}
