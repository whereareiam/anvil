package me.whereareiam.anvil.environment.provisioning.workspace.api.model;

import lombok.Value;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The current source of one of a prepared workspace's assets, named by the asset's target.
 */
@Value
public class AssetRefresh {
	/**
	 * The asset's target in the workspace, as its plan names it.
	 */
	@NotNull Path target;
	/**
	 * Where the asset's content is now, such as a workbench's copy of a plugin JAR rebuilt since preparation.
	 */
	@NotNull AssetSource source;
}
