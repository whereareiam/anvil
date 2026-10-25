package me.whereareiam.anvil.workbench.server;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ArtifactDocument;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ArtifactRequest;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ObtainArtifact;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ObtainedArtifact;
import me.whereareiam.anvil.workbench.protocol.model.artifact.ReadArtifact;
import me.whereareiam.anvil.workbench.protocol.model.file.CheckFile;
import me.whereareiam.anvil.workbench.protocol.model.file.DeleteFile;
import me.whereareiam.anvil.workbench.protocol.model.file.FileContent;
import me.whereareiam.anvil.workbench.protocol.model.file.FileDeletion;
import me.whereareiam.anvil.workbench.protocol.model.file.FileNames;
import me.whereareiam.anvil.workbench.protocol.model.file.FilePresence;
import me.whereareiam.anvil.workbench.protocol.model.file.FileRequest;
import me.whereareiam.anvil.workbench.protocol.model.file.ListFiles;
import me.whereareiam.anvil.workbench.protocol.model.file.ReadFile;
import me.whereareiam.anvil.workbench.protocol.model.file.WriteFile;
import me.whereareiam.anvil.workbench.protocol.model.java.InspectJava;
import me.whereareiam.anvil.workbench.protocol.model.java.InspectJavaSource;
import me.whereareiam.anvil.workbench.protocol.model.java.InspectedJava;
import me.whereareiam.anvil.workbench.protocol.model.java.JavaRequest;
import me.whereareiam.anvil.workbench.protocol.model.java.ResolveJava;
import me.whereareiam.anvil.workbench.protocol.model.java.ResolvedJava;
import me.whereareiam.anvil.workbench.protocol.model.java.ValidateJava;
import me.whereareiam.anvil.workbench.protocol.model.workspace.DeleteRunDirectory;
import me.whereareiam.anvil.workbench.protocol.model.workspace.FinishRunDirectory;
import me.whereareiam.anvil.workbench.protocol.model.workspace.FinishWorkspace;
import me.whereareiam.anvil.workbench.protocol.model.workspace.LayoutWorkspaces;
import me.whereareiam.anvil.workbench.protocol.model.workspace.PrepareWorkspace;
import me.whereareiam.anvil.workbench.protocol.model.workspace.PreparedWorkspaceHandle;
import me.whereareiam.anvil.workbench.protocol.model.workspace.RecreateRunDirectory;
import me.whereareiam.anvil.workbench.protocol.model.workspace.WorkspaceProvisioningRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Answers an engine's requests about the workbench's artifacts, Java installations, workspaces and process files.
 */
@RequiredArgsConstructor
final class ProvisioningOperations {
	private final @NotNull Workbench workbench;
	private final @NotNull Handles handles;
	private final @NotNull String owner;

	@NotNull Object artifact(@NotNull ArtifactRequest<?> request) {
		return switch (request) {
			case ObtainArtifact obtain -> new ObtainedArtifact(workbench.artifacts().obtain(obtain.getUri(), obtain.getDestination(),
					obtain.getSha256()));
			case ReadArtifact read -> new ArtifactDocument(workbench.artifacts().read(read.getUri()));
		};
	}

	@NotNull Object java(@NotNull JavaRequest<?> request) {
		return switch (request) {
			case ResolveJava resolve -> new ResolvedJava(workbench.java().resolve(resolve.getRequirement(), resolve.getSource()));
			case InspectJavaSource inspect -> new InspectedJava(workbench.java().inspect(inspect.getSource()).orElse(null));
			case InspectJava inspect -> new InspectedJava(workbench.java().inspect(inspect.getProperties(), inspect.getExecutable()));
			case ValidateJava validate -> {
				workbench.java().validate(validate.getInstallation(), validate.getRequirement());
				yield new Done();
			}
		};
	}

	/**
	 * Answers a workspace request; text assets arrive filled in by the engine, where their placeholders resolve.
	 */
	@NotNull Object workspace(@NotNull WorkspaceProvisioningRequest<?> request) {
		var workspaces = workbench.workspaces();
		return switch (request) {
			case LayoutWorkspaces layout -> workspaces.layout(layout.getRoot(), layout.getScenario());
			case PrepareWorkspace prepare -> {
				PreparedWorkspace prepared = workspaces.prepare(WorkspaceRequest.builder()
						.root(prepare.getRoot())
						.directory(prepare.getDirectory())
						.process(prepare.getProcess())
						.plan(prepare.getPlan())
						.providerDefaults(prepare.getProviderDefaults())
						.build());
				yield new PreparedWorkspaceHandle(handles.register(owner, prepared, () -> prepared.finish(false)), prepared.workspace());
			}
			case FinishWorkspace finish -> {
				if (handles.remove(owner, finish.getHandle()) instanceof PreparedWorkspace prepared) prepared.finish(finish.isSuccessful());
				yield new Done();
			}
			case RecreateRunDirectory recreate -> {
				workspaces.recreateRunDirectory(recreate.getRoot(), recreate.getDirectory());
				yield new Done();
			}
			case FinishRunDirectory finish -> {
				workspaces.finishRunDirectory(finish.getRoot(), finish.getDirectory(), finish.isSuccessful(), finish.isKeepFailed());
				yield new Done();
			}
			case DeleteRunDirectory delete -> {
				workspaces.deleteRunDirectory(delete.getRoot(), delete.getDirectory());
				yield new Done();
			}
		};
	}

	@NotNull Object file(@NotNull FileRequest<?> request) throws IOException {
		return switch (request) {
			case CheckFile check -> new FilePresence(files(check.getWorkspace()).exists(check.getPath()));
			case ReadFile read -> new FileContent(files(read.getWorkspace()).read(read.getPath()));
			case WriteFile write -> {
				files(write.getWorkspace()).write(write.getPath(), write.getContent());
				yield new Done();
			}
			case ListFiles list -> new FileNames(files(list.getWorkspace()).list(list.getPath()));
			case DeleteFile delete -> new FileDeletion(files(delete.getWorkspace()).delete(delete.getPath()));
		};
	}

	private ProcessFiles files(Path workspace) {
		return workbench.workspaces().files(workspace);
	}
}
