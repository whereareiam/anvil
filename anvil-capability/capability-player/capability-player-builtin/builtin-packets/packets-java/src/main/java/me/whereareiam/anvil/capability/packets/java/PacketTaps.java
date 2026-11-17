package me.whereareiam.anvil.capability.packets.java;

import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.packets.channel.TapRequest;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPackets;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.JavaPacketData;
import me.whereareiam.anvil.protocol.java.packet.PacketRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open taps of one player: each takes the frames its request names, and while at least one is open the taps
 * listen to the player's frames, sending each frame the open taps take once, with the IDs of those taps and with its
 * bytes when one of them needs them. Names are checked against Anvil's packet data for the player's protocol, so a
 * misspelt name is refused rather than never matched.
 */
final class PacketTaps {
	private final PlayerBindingContext<Object> player;
	private final JavaProtocol protocol;
	private final @Nullable PacketRegistry names;
	private final Map<Integer, Tap> taps = new ConcurrentHashMap<>();
	/**
	 * The subscription to the player's frames while a tap is open; guarded by this.
	 */
	private @Nullable Subscription frames;

	PacketTaps(@NotNull PlayerBindingContext<Object> player, @NotNull JavaProtocol protocol) {
		this.player = player;
		this.protocol = protocol;
		this.names = JavaPacketData.shipped().find(protocol.protocolNumber()).orElse(null);
	}

	/**
	 * Registers the host's opening and closing of taps.
	 */
	void register(@NotNull OperationRegistry operations) {
		operations.register(PacketsChannel.TAP, this::open);
		operations.register(PacketsChannel.UNTAP, this::close);
	}

	/**
	 * Closes every tap and stops listening to the player's frames.
	 */
	synchronized void closeAll() {
		taps.clear();
		stopListening();
	}

	private synchronized Integer open(TapRequest request) {
		Tap tap = new Tap(selection(request), request.isPayloads());
		if (taps.putIfAbsent(request.getId(), tap) != null)
			throw new IllegalArgumentException("Tap " + request.getId() + " is already open");
		if (frames == null) frames = protocol.listenFrames(this::received);

		return protocol.protocolNumber();
	}

	private synchronized Void close(Integer id) {
		taps.remove(id);
		if (taps.isEmpty()) stopListening();

		return null;
	}

	private void stopListening() {
		if (frames == null) return;

		frames.close();
		frames = null;
	}

	/**
	 * Sends a frame once for the open taps that take it.
	 */
	private void received(ClientboundFrame frame) {
		String name = frame.name().orElse(null);
		ClientboundPacket packet = frame.packet().orElse(null);
		List<Integer> taking = new ArrayList<>();
		boolean payloads = false;
		for (Map.Entry<Integer, Tap> tap : taps.entrySet()) {
			if (!tap.getValue().selection().selects(name, packet)) continue;

			taking.add(tap.getKey());
			payloads |= tap.getValue().payloads();
		}
		if (taking.isEmpty()) return;

		player.emit(PacketsChannel.FRAMES, new TappedFrame(List.copyOf(taking), frame.getState(), frame.getId(), name,
				payloads ? frame.payload() : null, packet));
	}

	/**
	 * Reads the selection a request names, checking each kind and name.
	 */
	private PacketSelection selection(TapRequest request) {
		if (request.isEvery()) return PacketSelection.every();

		List<PacketSelection> parts = new ArrayList<>();
		if (!request.getKinds().isEmpty()) parts.add(PacketSelection.of(kinds(request.getKinds())));
		if (!request.getNames().isEmpty()) parts.add(PacketSelection.named(names(request.getNames())));
		if (parts.isEmpty()) throw new IllegalArgumentException("Tap " + request.getId() + " takes no packet");

		return parts.stream().reduce(PacketSelection::and).orElseThrow();
	}

	private static List<Class<? extends ClientboundPacket>> kinds(Set<String> kinds) {
		List<Class<? extends ClientboundPacket>> types = new ArrayList<>();
		for (String kind : kinds)
			types.add(ClientboundPackets.find(kind).orElseThrow(() -> new IllegalArgumentException(
					"The Java protocol has no packet of kind " + kind)));

		return types;
	}

	/**
	 * Checks that the player's protocol has a packet of each name in some state.
	 */
	private Set<String> names(Set<String> requested) {
		if (names == null)
			throw new IllegalArgumentException("Anvil ships no packet names for protocol " + protocol.protocolNumber()
					+ ", so a tap cannot take packets by name; take them by class or take every packet");

		for (String name : requested) {
			boolean known = false;
			for (ConnectionState state : ConnectionState.values())
				known |= names.clientboundId(state, name).isPresent();
			if (!known)
				throw new IllegalArgumentException("Protocol " + protocol.protocolNumber() + " has no packet named "
						+ name + " that a server sends");
		}

		return requested;
	}

	/**
	 * What one open tap takes, and whether it needs the bytes of what it takes.
	 */
	private record Tap(PacketSelection selection, boolean payloads) {
	}
}
