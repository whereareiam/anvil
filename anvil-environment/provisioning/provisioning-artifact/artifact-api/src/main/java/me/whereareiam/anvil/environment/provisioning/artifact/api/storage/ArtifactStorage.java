package me.whereareiam.anvil.environment.provisioning.artifact.api.storage;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Grants exclusive access to one downloaded artifact or metadata file.
 * Storage adapters own entry coordination and publication; acquisition owns content policy.
 */

public interface ArtifactStorage {
	/**
	 * Acquires exclusive file access without changing its current contents.
	 * Use and close the entry on the acquiring thread.
	 *
	 * @param destination artifact or metadata location selected by acquisition
	 * @return caller-owned file access
	 * @throws IOException when exclusive storage access cannot be acquired
	 */
	@NotNull ArtifactEntry open(@NotNull Path destination) throws IOException;
}
