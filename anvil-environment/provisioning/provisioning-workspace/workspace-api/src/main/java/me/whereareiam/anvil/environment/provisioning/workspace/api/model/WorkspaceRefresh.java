package me.whereareiam.anvil.environment.provisioning.workspace.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * What a prepared workspace is brought up to date with before a generation of its process: everything that may have
 * changed since preparation and that the workspace cannot learn itself. Each member that is left empty changes
 * nothing.
 */
@Value
@Builder
public class WorkspaceRefresh {
	/**
	 * The current sources of the workspace's file assets. An asset not listed keeps its planned source.
	 */
	@NotNull @Singular List<AssetRefresh> assets;
}
