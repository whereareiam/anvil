package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Locked runtime closures of MCProtocolLib releases, resolved by Gradle exactly as release data lists them and
 * named in a file passed to the test JVM, so worker tests start real workers on what a worker downloads without
 * downloading it.
 */
public final class GradleReleaseClosures {
	private static final String PROPERTY = "anvil.test.mcprotocol.closures";

	/**
	 * Returns the closure of one release in classpath order.
	 *
	 * @param release built-in release
	 * @return Gradle-resolved closure files
	 * @throws IllegalStateException when the test JVM was not started by the module's Gradle test task
	 */
	public static @NotNull List<Path> of(@NotNull ProtocolRelease release) {
		String files = closures().getProperty(release.getLibraryVersion(), "");
		if (files.isBlank())
			throw new IllegalStateException("No Gradle-resolved closure for MCProtocolLib " + release.getLibraryVersion()
					+ "; run the tests through the mcprotocol-common Gradle test task");

		return Arrays.stream(files.split(Pattern.quote(File.pathSeparator))).map(Path::of).toList();
	}

	private static Properties closures() {
		String location = System.getProperty(PROPERTY, "");
		if (location.isBlank())
			throw new IllegalStateException("The test JVM names no release closures; run the tests through the mcprotocol-common Gradle test task");

		Properties closures = new Properties();
		try (InputStream input = Files.newInputStream(Path.of(location))) {
			closures.load(input);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read the release closures " + location, exception);
		}
		return closures;
	}
}
