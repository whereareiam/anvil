package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The runtime values of one generation of a container process: its environment and the command that replaces its
 * image's own. The image and its ports belong to the process's request.
 */
@Value
@Builder(toBuilder = true)
public class ImageLaunch implements ProcessLaunch {
	@NotNull
	@Singular("environmentVariable")
	Map<String, String> environment;

	/**
	 * The command that replaces the image's own; empty for the image's own.
	 */
	@NotNull
	@Singular("argument")
	List<String> command;
}
