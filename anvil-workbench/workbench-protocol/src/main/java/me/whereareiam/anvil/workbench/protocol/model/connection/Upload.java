package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Uploads a file, or a zipped directory, of the engine's machine.
 */
@Value
public class Upload implements EngineConnection {
	/**
	 * The engine's token.
	 */
	@NotNull String token;

	/**
	 * SHA-256 of the content.
	 */
	@NotNull String sha256;

	/**
	 * File or directory name.
	 */
	@NotNull String name;

	/**
	 * Whether the content is a zipped directory.
	 */
	boolean directory;

	/**
	 * Size of the content in bytes.
	 */
	long size;
}
