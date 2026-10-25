package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The workbench keeps an upload.
 */
@Value
public class Uploaded implements ConnectionAnswer {
	/**
	 * Where it keeps it.
	 */
	@NotNull Path path;
}
