package me.whereareiam.anvil.api.model.java;

import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Path;

/**
 * Explicit origin for a Java runtime used by a scenario process.
 *
 * <p>Sources are execution-neutral declarations. The selected execution provider decides whether
 * the source can be used in its environment.</p>
 */
public interface JavaSource {
	/**
	 * Creates a source backed by an installed Java home.
	 *
	 * @param home JDK home directory
	 * @return Java home source
	 */
	static @NotNull JavaSource home(@NotNull Path home) {
		return new LocalJavaHome(home);
	}

	/**
	 * Creates a source backed by one Java executable.
	 *
	 * @param executable Java executable path
	 * @return executable source
	 */
	static @NotNull JavaSource executable(@NotNull Path executable) {
		return new LocalJavaExecutable(executable);
	}

	/**
	 * Creates a source backed by a checksum-verified archive.
	 *
	 * @param uri archive location
	 * @param sha256 expected archive SHA-256
	 * @return archive source
	 */
	static @NotNull JavaSource archive(@NotNull URI uri, @NotNull String sha256) {
		return JavaArchive.builder().uri(uri).sha256(sha256).build();
	}
}
