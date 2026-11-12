package me.whereareiam.anvil.environment.provisioning.java.installation;

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

	public static String executableName(String operatingSystem) {
		return operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows") ? "java.exe" : "java";
	}

}
