package me.whereareiam.anvil.workbench.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * A JVM a workbench starts outside managed execution, such as a protocol library's player worker: one main class
 * on a class path, talking to the engine over its standard streams.
 */
@Value
@Builder
public class JavaMainLaunch {
	/**
	 * Class path entries in order; every entry is a path on the workbench.
	 */
	@NotNull
	@Singular("classpathEntry")
	List<Path> classpath;

	/**
	 * Fully qualified name of the main class.
	 */
	@NotNull String mainClass;

	/**
	 * Arguments to the JVM before the class path.
	 */
	@NotNull
	@Singular
	List<String> jvmArguments;

	/**
	 * Arguments to the main class.
	 */
	@NotNull
	@Singular
	List<String> arguments;

	/**
	 * Lowest Java feature version the JVM runs on.
	 */
	int javaVersion;
}
