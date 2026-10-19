package me.whereareiam.anvil.environment.execution.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Inputs required to prepare a process location before platform configuration.
 */
@Value
@Builder(toBuilder = true)
public class ProcessRequest {
	@NotNull String name;
	@NotNull Path workspace;

	/**
	 * Planned Java selection whose requirement carries the exact feature version to run, with an
	 * optional explicit installation source; null for a process that runs a container {@link #image}.
	 */
	@Nullable JavaSelection javaSelection;

	/**
	 * The image the process runs instead of a Java application; null for a Java application.
	 */
	@Nullable ContainerImage image;

	boolean agent;
	@Builder.Default
	boolean publishGame = true;

	/**
	 * Names of the listeners the process binds besides its game port, each needing a port of its own.
	 */
	@NotNull
	@Singular
	List<String> listeners;
}
