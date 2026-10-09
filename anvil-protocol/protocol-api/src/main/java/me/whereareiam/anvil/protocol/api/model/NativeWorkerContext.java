package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Identity of one native worker runtime, supplied to every {@link NativeWorkerProvider} before players exist.
 */
@Value
@Builder
public class NativeWorkerContext {
	/**
	 * Identifier of the protocol library that owns the worker.
	 */
	@NotNull String libraryId;
	/**
	 * Release key version loaded by the worker.
	 */
	@NotNull MinecraftVersion version;
	/**
	 * Native wire-protocol number verified by the worker.
	 */
	int protocolNumber;
	/**
	 * Class of the native session objects that the worker passes to bindings.
	 */
	@NotNull Class<?> nativeSessionType;
}
