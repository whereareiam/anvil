package me.whereareiam.anvil.engine.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.engine.AnvilException;
import me.whereareiam.anvil.engine.model.EngineOptions;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Selects a configured, current, environment-supplied, or downloaded Java executable.
 */
@RequiredArgsConstructor
public final class ProcessJavaResolver {
	private final @NotNull EngineOptions options;
	private final @NotNull TemurinRuntimeProvisioner runtimes;

	public @NotNull Path resolve(int minimumVersion) {
		Path configured = options.getJavaExecutables().get(minimumVersion);
		if (configured != null)
			return configured;
		if (Runtime.version().feature() >= minimumVersion)
			return options.getDefaultJavaExecutable();

		String variable = "JAVA_" + minimumVersion + "_HOME";
		String javaHome = System.getenv(variable);
		if (javaHome != null) {
			String executableName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
					? "java.exe" : "java";
			Path executable = Path.of(javaHome, "bin", executableName);
			if (Files.isExecutable(executable))
				return executable;
		}
		if (options.isAutoDownloadJavaRuntimes())
			return runtimes.resolve(minimumVersion, options.getCacheDirectory());
		throw new AnvilException("Java " + minimumVersion + "+ is required. Configure anvil.javaExecutables["
				+ minimumVersion + "] or " + variable);
	}
}
