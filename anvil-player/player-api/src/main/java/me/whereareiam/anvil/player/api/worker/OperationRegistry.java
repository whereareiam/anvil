package me.whereareiam.anvil.player.api.worker;

import me.whereareiam.anvil.player.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Registers one player's operation handlers together with their request and response schemas while an extension
 * binds the player. The worker rejects registrations outside that phase and identifiers that are not namespaced
 * or already taken; it decodes requests and checks handler results against these schemas before encoding
 * responses.
 */
public interface OperationRegistry {
	/**
	 * Associates a typed operation with its handler.
	 *
	 * @param operation operation identity and request/response schemas
	 * @param handler typed request handler; a {@link Void} request is supplied as {@code null}, a handler with a
	 *                {@link Void} response returns {@code null}, and every other handler returns a value, which
	 *                the worker checks through {@link OperationDescriptor#response(Object)}
	 * @param <Q> request type
	 * @param <R> response type
	 */
	<Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler);
}
