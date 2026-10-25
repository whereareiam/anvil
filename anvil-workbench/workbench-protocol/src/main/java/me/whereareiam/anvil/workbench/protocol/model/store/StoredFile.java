package me.whereareiam.anvil.workbench.protocol.model.store;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Where the workbench keeps content, if it holds it.
 */
@Value
public class StoredFile {
	/**
	 * Path on the workbench, or null when it does not hold the content.
	 */
	@Nullable Path path;
}
