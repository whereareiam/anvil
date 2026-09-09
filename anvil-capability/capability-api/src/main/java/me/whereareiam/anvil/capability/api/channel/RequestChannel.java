package me.whereareiam.anvil.capability.api.channel;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Invokes typed operations through a borrowed capability request channel.
 * The supplying backend owns serialization, transport, and connection lifetime. An operation can
 * be unavailable while its process or player reconnects; this contract does not expose connection
 * management or imply event-subscription support.
 */
public interface RequestChannel {
	/**
	 * Executes a registered operation using its declared request and response schemas.
	 * The operation and backend define whether an absent response is valid. A capability that
	 * requires a result must reject its absence; connection access is not exposed by this contract.
	 *
	 * @param channelOperation namespaced operation and its schemas
	 * @param request request value, or {@code null} for a {@link Void} request
	 * @param <Q> request type
	 * @param <R> response type
	 * @return decoded response, or {@code null} when the operation returns no value
	 */
	@Nullable <Q, R> R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request);
}
