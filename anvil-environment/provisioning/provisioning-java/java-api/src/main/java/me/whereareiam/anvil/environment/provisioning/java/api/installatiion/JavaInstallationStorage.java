package me.whereareiam.anvil.environment.provisioning.java.api.installatiion;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Coordinates exclusive inspection, extraction, and publication of a prepared Java installation.
 */
public interface JavaInstallationStorage {
	/**
	 * Acquires installation ownership across threads and cooperating processes.
	 * The caller retains ownership through validation and marker publication.
	 *
	 * @param installation installation location selected by Java provisioning
	 * @return caller-owned access, used and closed on the acquiring thread
	 * @throws IOException when ownership cannot be acquired
	 */
	@NotNull JavaInstallationAccess acquire(@NotNull Path installation) throws IOException;
}
