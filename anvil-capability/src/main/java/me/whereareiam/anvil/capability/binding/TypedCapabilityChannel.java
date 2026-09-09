package me.whereareiam.anvil.capability.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.MessageChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Applies typed capability schemas to a borrowed encoded-message transport.
 */
@RequiredArgsConstructor
public final class TypedCapabilityChannel implements CapabilityChannel {
	private final @NotNull MessageChannel messages;
	private final JsonCapabilityCodec codec = new JsonCapabilityCodec();

	@Override
	public @Nullable <Q, R> R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
		Q checked = channelOperation.getRequestType().cast(request);
		if (checked == null && channelOperation.getRequestType() != Void.class)
			throw new IllegalArgumentException("Missing request for capability operation '" + channelOperation.getId() + "'");

		byte[] response = messages.request(channelOperation.getId(), codec.encode(checked));
		return codec.decode(response, channelOperation.getResponseType());
	}

	@Override
	public @NotNull <E> Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
		return messages.subscribe(eventDescriptor.getId(), encoded -> listener.accept(codec.decode(encoded, eventDescriptor.getPayloadType())));
	}

	@Override
	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		messages.await(condition, description, timeout);
	}

	@Override
	public @NotNull Set<String> installedCapabilities() { return messages.installedCapabilities(); }
}
