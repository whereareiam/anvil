package me.whereareiam.anvil.environment.execution.api.image;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Coordinates image metadata operations across threads and processes.
 * Image selection, metadata formats, and file layout belong to the execution provider.
 */
public interface ImageLocks {
	/**
	 * Acquires exclusive ownership of one provider-selected metadata directory.
	 *
	 * @param directory location selected by the image policy
	 * @return owned coordination lease
	 * @throws IOException when coordination cannot be acquired
	 */
	@NotNull ImageLease acquire(@NotNull Path directory) throws IOException;
}
