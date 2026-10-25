package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Asks whether a file or directory exists.
 */
@Value
public class CheckFile implements FileRequest<FilePresence> {
	/**
	 * Workspace on the workbench.
	 */
	@NotNull Path workspace;

	/**
	 * Workspace-relative path.
	 */
	@NotNull String path;

	@Override
	public @NotNull Class<FilePresence> responseType() {
		return FilePresence.class;
	}
}
