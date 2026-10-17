package me.whereareiam.anvil.capability.protocol.api.player.worker;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Installs capability-owned behavior into the native worker of a protocol library.
 * The generic type is the library's actual native session type, not an Anvil packet abstraction.
 *
 * <p>Extensions are discovered through {@link ServiceLoader} from
 * {@code META-INF/services/me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension}
 * inside the worker. A worker installs an extension when the extension targets the worker's library
 * (or any library) and accepts the worker's native session type. An extension whose binding fails to
 * link against the loaded library release, for example with a {@link NoClassDefFoundError}, is reported
 * as unavailable with the missing member instead of failing the player. So is an extension whose
 * {@link PlayerBindingContext#adapter(Class) adapter lookup} fails with {@link AdapterUnavailableException}
 * because no segment selected for the loaded release provides its port, or the segment does not link.</p>
 *
 * <p>An extension takes every release-specific implementation from that lookup while it binds a player, never
 * from a service lookup of its own, and its constructor loads nothing: a worker creates every discovered
 * extension before it knows which ones it installs.</p>
 *
 * <pre>{@code
 * public final class ExampleExtension implements WorkerExtension<Object> {
 *     public String id() { return "example.capability"; }
 *     public Optional<String> libraryId() { return Optional.of("mcprotocol"); }
 *     public Class<Object> nativeSessionType() { return Object.class; }
 *     public WorkerBinding bind(PlayerBindingContext<Object> player, OperationRegistry operations) {
 *         ExamplePackets<?> packets = player.adapter(ExamplePackets.class);
 *         operations.register(ExampleOperations.PING, ignored -> null);
 *         return () -> { };
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type accepted by this extension
 */
public interface WorkerExtension<S> {
	/**
	 * Returns the capability ID also declared by the host-side provider.
	 *
	 * @return stable capability ID
	 */
	@NotNull String id();

	/**
	 * Returns the protocol library this extension targets.
	 *
	 * @return library identifier, or empty when the extension works with every library
	 */
	@NotNull Optional<String> libraryId();

	/**
	 * Returns the native session type this binding casts to. A worker accepts the extension when this
	 * type is assignable from the worker's own session type, so a supertype such as a session interface
	 * may be declared.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> nativeSessionType();

	/**
	 * Creates isolated state and handlers for one player before that player is exposed.
	 * Operations may be registered only during the player-binding phase. Their IDs must be
	 * namespaced and unique across that player's bindings. The supplied registry enforces these
	 * restrictions while the transport bridge owns schema conversion and response validation.
	 *
	 * @param player native lifecycle, session access, and event delivery for this player's binding
	 * @param operations typed operation registration for this player
	 * @return owned binding closed on player destruction or rollback after a later binding fails
	 */
	@NotNull WorkerBinding bind(@NotNull PlayerBindingContext<S> player, @NotNull OperationRegistry operations);
}
