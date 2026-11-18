package me.whereareiam.anvil.capability.packets.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.player.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.player.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host packets capability and its worker extensions: the capability ID both register
 * under, the operations that open and close taps and the event that carries the frames of open taps.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PacketsChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.packets";
	/**
	 * Starts sending the frames a tap takes as {@link #FRAMES}, and answers the number of the protocol the player
	 * speaks.
	 */
	public static final OperationDescriptor<TapRequest, Integer> TAP = new OperationDescriptor<>(
			"packets.tap", TapRequest.class, Integer.class
	);
	/**
	 * Stops sending the frames of the tap with this ID.
	 */
	public static final OperationDescriptor<Integer, Void> UNTAP = new OperationDescriptor<>(
			"packets.untap", Integer.class, Void.class
	);
	/**
	 * A frame the player received that open taps take.
	 */
	public static final EventDescriptor<TappedFrame> FRAMES = new EventDescriptor<>(
			"packets.frames", TappedFrame.class
	);
}
