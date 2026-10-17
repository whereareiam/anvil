package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import org.jetbrains.annotations.NotNull;

/**
 * Service-loaded assembly bridge installing extensions into a native worker runtime.
 * Every provider on the worker classpath is created once per worker, before players exist.
 */
public interface NativeWorkerProvider {
	/**
	 * Returns the unique provider ID.
	 * @return provider ID
	 */
	@NotNull String id();

	/**
	 * Prepares extension installation for one worker runtime. Native sessions passed to the returned
	 * extension are instances of {@link NativeWorkerContext#getNativeSessionType()}.
	 *
	 * @param context owning library, release key version, protocol number, and native session type
	 * @return prepared worker extension, possibly installing nothing for a library it does not target
	 */
	@NotNull NativeWorkerExtension<Object> create(@NotNull NativeWorkerContext context);
}
