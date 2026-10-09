package me.whereareiam.anvil.environment.execution.api.model.process;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

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
	 * optional explicit installation source.
	 */
	@NotNull JavaSelection javaSelection;

	boolean agent;
	@Builder.Default
	boolean publishGame = true;
}
