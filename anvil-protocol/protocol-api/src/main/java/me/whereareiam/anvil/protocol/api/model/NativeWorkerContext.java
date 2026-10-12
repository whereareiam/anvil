package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftEdition;
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
	 * Minecraft edition of the release loaded by the worker.
	 */
	@NotNull MinecraftEdition edition;
	/**
	 * Release key version loaded by the worker.
	 */
	@NotNull MinecraftVersion version;
	/**
	 * Native wire-protocol number of the release key version, verified by the worker. A release may map its other
	 * versions to other numbers; each player's own number travels with its connection.
	 */
	int protocolNumber;
	/**
	 * Class of the native session objects that the worker passes to bindings.
	 */
	@NotNull Class<?> nativeSessionType;
}
