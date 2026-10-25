package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Writes a text file, creating its directories.
 */
@Value
public class WriteFile implements FileRequest<Done> {
	/**
	 * Workspace on the workbench.
	 */
	@NotNull Path workspace;

	/**
	 * Workspace-relative path.
	 */
	@NotNull String path;

	/**
	 * New content.
	 */
	@NotNull String content;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
