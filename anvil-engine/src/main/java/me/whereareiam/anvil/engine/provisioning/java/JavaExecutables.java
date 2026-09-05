package me.whereareiam.anvil.engine.provisioning.java;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves Java executable names and installation paths for the host operating system.
 */
public final class JavaExecutables {
	/**
	 * Returns the executable belonging to the current Java installation.
	 *
	 * @return current executable path
	 */
	public static @NotNull Path current() {
		return atHome(Path.of(System.getProperty("java.home")));
	}

	/**
	 * Resolves the Java executable inside an installation directory.
	 *
	 * @param home installation root
	 * @return platform-appropriate executable path
	 */
	public static @NotNull Path atHome(@NotNull Path home) {
		return atHome(home, System.getProperty("os.name", ""));
	}

	static Path atHome(Path home, String operatingSystem) {
		return home.resolve("bin").resolve(executableName(operatingSystem));
	}

	static String executableName(String operatingSystem) {
		return operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows") ? "java.exe" : "java";
	}

	static String temurinOperatingSystem(String operatingSystem) {
		String value = operatingSystem.toLowerCase(Locale.ROOT);
		if (value.startsWith("windows")) return "windows";
		if (value.contains("mac") || value.contains("darwin")) return "mac";
		if (value.contains("linux")) return "linux";

		throw new ProvisioningException("Automatic Java provisioning does not support this operating system: " + value);
	}
}
