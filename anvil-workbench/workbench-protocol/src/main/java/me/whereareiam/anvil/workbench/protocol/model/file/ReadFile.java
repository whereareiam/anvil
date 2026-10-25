package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Reads a text file.
 */
@Value
public class ReadFile implements FileRequest<FileContent> {
	/**
	 * Workspace on the workbench.
	 */
	@NotNull Path workspace;

	/**
	 * Workspace-relative path.
	 */
	@NotNull String path;

	@Override
	public @NotNull Class<FileContent> responseType() {
		return FileContent.class;
	}
}
