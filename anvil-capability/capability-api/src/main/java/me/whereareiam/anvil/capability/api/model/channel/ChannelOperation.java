package me.whereareiam.anvil.capability.api.model.channel;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Names one capability operation and its explicitly registered request and response schemas.
 * Schema changes require a new operation ID. Transport implementations must never resolve
 * payload class names supplied by a remote peer.
 *
 * @param <Q> immutable request value
 * @param <R> immutable response value; {@link Void} denotes a command without a result
 */
@Value
public class ChannelOperation<Q, R> {
	@NotNull String id;
	@NotNull Class<Q> requestType;
	@NotNull Class<R> responseType;
}
