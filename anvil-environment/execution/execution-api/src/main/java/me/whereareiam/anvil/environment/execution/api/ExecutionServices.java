package me.whereareiam.anvil.environment.execution.api;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.environment.execution.api.image.ImageLocks;
import me.whereareiam.anvil.environment.execution.api.runtime.LocalRuntimePreparation;
import me.whereareiam.anvil.environment.execution.api.runtime.RuntimeValidator;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The services of the machine execution sessions run on: its cache, Java runtimes, image coordination and ports.
 * The workbench of that machine supplies them; providers own execution, endpoint translation, image selection and
 * metadata layout.
 */
@Value
@Builder
public class ExecutionServices {
	/**
	 * Cache root of the machine.
	 */
	@NotNull Path cacheDirectory;
	@NotNull LocalRuntimePreparation localRuntime;
	@NotNull RuntimeValidator runtimeValidator;
	@NotNull ImageLocks imageLocks;
	/**
	 * Listener ports shared by every execution session of the machine.
	 */
	@NotNull PortReservations ports;
}
