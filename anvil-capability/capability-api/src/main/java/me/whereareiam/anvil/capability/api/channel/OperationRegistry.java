package me.whereareiam.anvil.capability.api.channel;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Registers operation handlers together with their request and response schemas.
 * The supplying owner defines registration lifetime and identifier restrictions. Transport bridges
 * decode requests and check handler results against these schemas before encoding responses.
 */
public interface OperationRegistry {
	/**
	 * Associates a typed operation with its handler.
	 *
	 * @param channelOperation operation identity and request/response schemas
	 * @param handler typed request handler; a {@link Void} request is supplied as {@code null},
	 *                and a handler with a {@link Void} response returns {@code null}
	 * @param <Q> request type
	 * @param <R> response type
	 */
	<Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler);
}
