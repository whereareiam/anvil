package me.whereareiam.anvil.capability.packets.tap;

import me.whereareiam.anvil.capability.packets.PacketRecording;
import me.whereareiam.anvil.capability.packets.PacketTap;
import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.packets.channel.TapRequest;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The taps and recordings of one player's Packets capability: each opens on the player's worker under an ID of its
 * own, so the worker sends only the frames open taps take, which this hands to every tap or recording a frame names.
 * A tap asks for values alone; a recording asks for every frame with its bytes.
 */
public final class ChannelPacketTaps {
	private final ProtocolChannel channel;
	private final String player;
	private final String minecraft;
	private final Map<Integer, Consumer<TappedFrame>> receivers = new ConcurrentHashMap<>();
	private final AtomicInteger ids = new AtomicInteger();

	/**
	 * Creates the taps of one player and starts receiving their frames.
	 *
	 * @param channel the player's channel
	 * @param player the player's name, which recordings name
	 * @param minecraft the Minecraft version of the player's client, which recordings name
	 */
	public ChannelPacketTaps(@NotNull ProtocolChannel channel, @NotNull String player, @NotNull String minecraft) {
		this.channel = channel;
		this.player = player;
		this.minecraft = minecraft;
		channel.subscribe(PacketsChannel.FRAMES, frame -> {
			for (int tap : frame.getTaps()) {
				Consumer<TappedFrame> receiver = receivers.get(tap);
				if (receiver != null) receiver.accept(frame);
			}
		});
	}

	/**
	 * Opens a tap of a selection.
	 *
	 * @param selection which packets the tap takes
	 * @return the open tap
	 */
	public @NotNull PacketTap tap(@NotNull PacketSelection selection) {
		int id = ids.incrementAndGet();
		ChannelPacketTap tap = new ChannelPacketTap(id, selection, channel, this::closed);
		open(id, tap::receive, new TapRequest(id, selection.isEvery(), kinds(selection), selection.getNames(), false));

		return tap;
	}

	/**
	 * Starts a recording of every packet.
	 *
	 * @return the running recording
	 */
	public @NotNull PacketRecording record() {
		int id = ids.incrementAndGet();
		RecordingReceiver receiver = new RecordingReceiver();
		int protocol = open(id, receiver, new TapRequest(id, true, Set.of(), Set.of(), true));

		return receiver.started(new ChannelPacketRecording(id, new RecordingHeader(RecordingHeader.FORMAT, protocol,
				minecraft, player), this::closed));
	}

	/**
	 * Registers a receiver before the worker starts sending its frames, so none is lost, and forgets it again when the
	 * worker refuses the tap.
	 *
	 * @return the number of the protocol the player speaks
	 */
	private int open(int id, Consumer<TappedFrame> receiver, TapRequest request) {
		receivers.put(id, receiver);
		try {
			return Objects.requireNonNull(channel.request(PacketsChannel.TAP, request), "The worker named no protocol");
		} catch (RuntimeException refused) {
			receivers.remove(id);
			throw refused;
		}
	}

	private void closed(int id) {
		receivers.remove(id);
		channel.request(PacketsChannel.UNTAP, id);
	}

	private static Set<String> kinds(PacketSelection selection) {
		return selection.getTypes().stream().map(ClientboundPackets::kind).collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Hands a recording's frames to it once it exists; frames the worker sends before the tap's answer arrives wait for
	 * it, in order.
	 */
	private static final class RecordingReceiver implements Consumer<TappedFrame> {
		private final List<TappedFrame> early = new ArrayList<>();
		private ChannelPacketRecording recording;

		@Override
		public synchronized void accept(TappedFrame frame) {
			if (recording == null) early.add(frame);
			else recording.receive(frame);
		}

		synchronized ChannelPacketRecording started(ChannelPacketRecording started) {
			recording = started;
			early.forEach(started::receive);
			early.clear();

			return started;
		}
	}
}
