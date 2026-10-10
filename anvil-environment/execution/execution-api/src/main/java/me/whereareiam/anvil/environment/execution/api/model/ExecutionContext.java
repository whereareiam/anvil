package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.environment.execution.api.PortReservations;
import me.whereareiam.anvil.environment.execution.api.image.ImageLocks;
import me.whereareiam.anvil.environment.execution.api.runtime.LocalRuntimePreparation;
import me.whereareiam.anvil.environment.execution.api.runtime.RuntimeValidator;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Execution policy and focused preparation boundaries for a scenario's process locations.
 * Runtime acquisition and image coordination are supplied by the caller; providers own execution,
 * endpoint translation, image selection, and metadata layout.
 */
@Value
@Builder(toBuilder = true)
public class ExecutionContext {
	@NotNull Path cacheDirectory;
	@NotNull LocalRuntimePreparation localRuntime;
	@NotNull RuntimeValidator runtimeValidator;
	@NotNull ImageLocks imageLocks;
	/**
	 * Listener ports shared by every execution session of the engine.
	 */
	@NotNull PortReservations ports;
	boolean offline;
	boolean refresh;

	@NotNull
	@Builder.Default
	NetworkPolicy networkPolicy = NetworkPolicy.builder().build();
}
