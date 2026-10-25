package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Deletes a file or a directory with its contents.
 */
@Value
public class DeleteFile implements FileRequest<FileDeletion> {
	/**
	 * Workspace on the workbench.
	 */
	@NotNull Path workspace;

	/**
	 * Workspace-relative path.
	 */
	@NotNull String path;

	@Override
	public @NotNull Class<FileDeletion> responseType() {
		return FileDeletion.class;
	}
}
