package me.whereareiam.anvil.environment.provisioning.workspace.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Resolved inputs for preparing one process directory.
 * Named artifacts must already be resolved to local paths; asset contents must remain stable during preparation.
 */
@Value
@Builder
public class WorkspaceRequest {
	@NotNull Path root;
	@NotNull Path directory;
	@NotNull MinecraftProcess process;
	@NotNull WorkspacePlan plan;

	@NotNull
	@Singular("providerDefault")
	List<WorkspaceCache> providerDefaults;

	/**
	 * Fills in the text of each text asset just before it is written, such as the addresses of the scenario's
	 * services. Snapshots are still told apart by the declared text, so values that change between runs keep their
	 * caches.
	 */
	@NotNull
	@Builder.Default
	UnaryOperator<String> text = UnaryOperator.identity();
}
