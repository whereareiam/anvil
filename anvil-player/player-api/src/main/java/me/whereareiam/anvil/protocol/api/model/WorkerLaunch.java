package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolWorkerLauncher;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * What a {@link ProtocolWorkerLauncher} starts: a JVM running one main class on a class path made of the library's
 * runtime closure and entries of the engine's own class path.
 */
@Value
@Builder
public class WorkerLaunch {
	/**
	 * The library's runtime closure in class path order, as the context's {@link ProtocolArtifactResolver} resolved
	 * it; it already lies where the worker runs.
	 */
	@NotNull
	@Singular("closureEntry")
	List<Path> closure;

	/**
	 * Entries of the engine's own class path that follow the closure, such as the library's client segment; they
	 * are files of the engine's machine and are handed to where the worker runs.
	 */
	@NotNull
	@Singular("engineEntry")
	List<Path> engineEntries;

	/**
	 * Fully qualified name of the worker's main class.
	 */
	@NotNull String mainClass;

	/**
	 * Arguments to the JVM, such as system properties, before the class path.
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
	 * Lowest Java feature version the worker runs on.
	 */
	int javaVersion;
}
