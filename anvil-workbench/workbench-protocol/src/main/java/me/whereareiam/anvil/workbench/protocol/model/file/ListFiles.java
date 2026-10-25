package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Lists a directory's entries.
 */
@Value
public class ListFiles implements FileRequest<FileNames> {
	/**
	 * Workspace on the workbench.
	 */
	@NotNull Path workspace;

	/**
	 * Workspace-relative directory, or an empty string for the workspace.
	 */
	@NotNull String path;

	@Override
	public @NotNull Class<FileNames> responseType() {
		return FileNames.class;
	}
}
