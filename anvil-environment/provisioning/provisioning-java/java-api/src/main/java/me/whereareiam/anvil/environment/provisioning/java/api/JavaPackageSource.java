package me.whereareiam.anvil.environment.provisioning.java.api;

import me.whereareiam.anvil.api.model.java.JavaArchive;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Obtains Java catalog documents and content-pinned installation archives.
 * Java provisioning owns catalog selection, installation layout, and archive extraction.
 */
public interface JavaPackageSource {
	/**
	 * Reads catalog metadata according to the configured acquisition policy.
	 * @param uri Java catalog resource
	 * @return catalog document
	 * @throws IOException when catalog content cannot be read
	 */
	@NotNull String catalog(@NotNull URI uri) throws IOException;

	/**
	 * Obtains a Java archive and verifies its declared checksum before returning it.
	 * @param archive content-pinned archive source
	 * @param destination archive location selected by Java provisioning
	 * @return verified local archive
	 * @throws IOException when verified content cannot be obtained
	 */
	@NotNull Path archive(@NotNull JavaArchive archive, @NotNull Path destination) throws IOException;
}
