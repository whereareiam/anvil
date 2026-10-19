package me.whereareiam.anvil.environment.execution.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The image a process runs instead of a Java application, with the ports it listens on, which the execution
 * provider reserves and publishes before the process starts.
 */
@Value
@Builder
public class ContainerImage {
	@NotNull String image;

	/**
	 * Ports the container listens on; the first is its main port.
	 */
	@NotNull
	@Singular
	List<Integer> ports;
}
