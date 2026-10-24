package me.whereareiam.anvil.workbench.remote.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Lays out, prepares and retains process workspaces on a workbench on another machine.
 */
@RequiredArgsConstructor
public final class RemoteWorkspaces implements WorkspaceProvisioner {
	private final @NotNull WorkbenchConnection connection;

	@Override
	public @NotNull WorkspaceLayout layout(@NotNull Path root, @NotNull AnvilScenario scenario) {
		ObjectNode arguments = connection.mapper().createObjectNode().put("root", root.toString());
		arguments.set("scenario", connection.mapper().valueToTree(scenario));

		return connection.mapper().convertValue(connection.call("workspaces.layout", arguments), WorkspaceLayout.class);
	}

	/**
	 * Prepares a workspace; text assets are filled in on the engine, where their placeholders resolve.
	 */
	@Override
	public @NotNull PreparedWorkspace prepare(@NotNull WorkspaceRequest request) {
		ObjectMapper mapper = connection.mapper();
		WorkspacePlan plan = request.getPlan().toBuilder().clearAssets().assets(request.getPlan().getAssets().stream()
				.map(asset -> asset.getSource().isText()
						? asset.toBuilder().source(AssetSource.text(request.getText().apply(asset.getSource().getText()))).build()
						: asset)
				.toList()).build();
		ObjectNode arguments = mapper.createObjectNode()
				.put("root", request.getRoot().toString())
				.put("directory", request.getDirectory().toString());
		arguments.set("process", mapper.valueToTree(request.getProcess()));
		arguments.set("plan", mapper.valueToTree(plan));
		arguments.set("providerDefaults", mapper.valueToTree(request.getProviderDefaults()));

		var prepared = connection.call("workspaces.prepare", arguments);
		return new RemotePreparedWorkspace(connection, prepared.path("handle").asText(), Path.of(prepared.path("workspace").asText()));
	}

	@Override
	public @NotNull ProcessFiles files(@NotNull Path workspace) {
		return new RemoteProcessFiles(connection, workspace);
	}

	@Override
	public void recreateRunDirectory(@NotNull Path root, @NotNull Path directory) {
		connection.call("workspaces.recreateRun", run(root, directory));
	}

	@Override
	public void finishRunDirectory(@NotNull Path root, @NotNull Path directory, boolean successful, boolean keepFailedWorkspaces) {
		connection.call("workspaces.finishRun", run(root, directory).put("successful", successful).put("keep", keepFailedWorkspaces));
	}

	@Override
	public void deleteRunDirectory(@NotNull Path root, @NotNull Path directory) {
		connection.call("workspaces.deleteRun", run(root, directory));
	}

	private ObjectNode run(Path root, Path directory) {
		return connection.mapper().createObjectNode().put("root", root.toString()).put("directory", directory.toString());
	}
}
