package me.whereareiam.anvil.capability.packets.tap;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.packets.PacketTap;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.TappedPacket;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * One open tap: it keeps the packets the worker sent for it, numbered in the order they arrived, and reads them through
 * a cursor, which each wait advances past the packet it returns.
 */
final class ChannelPacketTap implements PacketTap {
	private final int id;
	private final PacketSelection selection;
	private final ProtocolChannel channel;
	private final IntConsumer closer;
	private final List<TappedPacket> packets = new CopyOnWriteArrayList<>();
	private final HistoryCursor<TappedPacket> cursor = new HistoryCursor<>(() -> packets);
	private final AtomicBoolean closed = new AtomicBoolean();

	ChannelPacketTap(int id, @NotNull PacketSelection selection, @NotNull ProtocolChannel channel, @NotNull IntConsumer closer) {
		this.id = id;
		this.selection = selection;
		this.channel = channel;
		this.closer = closer;
	}

	@Override
	public @NotNull PacketSelection selection() {
		return selection;
	}

	@Override
	public <P extends ClientboundPacket> @NotNull P received(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition,
			@NotNull Duration timeout
	) {
		Predicate<TappedPacket> matcher = matcher(type, condition);
		channel.await(() -> cursor.find(matcher) != null, "receive a matching " + type.getSimpleName(), timeout);

		return Objects.requireNonNull(cursor.take(matcher)).packet(type).orElseThrow();
	}

	@Override
	public @NotNull TappedPacket received(@NotNull String name, @NotNull Duration timeout) {
		if (!selection.isEvery() && !selection.getNames().contains(name))
			throw new IllegalArgumentException("The tap takes no packets named " + name + "; it takes " + describe());

		Predicate<TappedPacket> matcher = packet -> packet.name().filter(name::equals).isPresent();
		channel.await(() -> cursor.find(matcher) != null, "receive a packet named " + name, timeout);

		return Objects.requireNonNull(cursor.take(matcher));
	}

	@Override
	public <P extends ClientboundPacket> void neverReceived(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition,
			@NotNull Duration duration
	) {
		Predicate<TappedPacket> matcher = matcher(type, condition);
		try {
			channel.await(() -> cursor.find(matcher) != null, "receive a matching " + type.getSimpleName(), duration);
		} catch (IllegalStateException quiet) {
			if (cursor.find(matcher) == null) return;
		}

		TappedPacket packet = Objects.requireNonNull(cursor.find(matcher));
		throw new IllegalStateException("Player received a " + type.getSimpleName() + " it must not, as packet #"
				+ packet.getSequence() + " of the tap: " + packet.packet().orElseThrow());
	}

	@Override
	public <P extends ClientboundPacket> @NotNull Expectation expectReceived(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition
	) {
		Predicate<TappedPacket> matcher = matcher(type, condition);
		return Expectation.of("receive a matching " + type.getSimpleName(), () -> cursor.find(matcher) != null);
	}

	@Override
	public @NotNull List<TappedPacket> history() {
		return List.copyOf(packets);
	}

	@Override
	public @NotNull List<TappedPacket> unread() {
		return cursor.unread();
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) closer.accept(id);
	}

	/**
	 * Keeps a packet the worker sent for the tap.
	 */
	void receive(@NotNull TappedFrame frame) {
		synchronized (packets) {
			packets.add(new TappedPacket(packets.size(), frame.getState(), frame.getId(), frame.getName(),
					frame.getPacket()));
		}
	}

	private <P extends ClientboundPacket> Predicate<TappedPacket> matcher(Class<P> type, Predicate<? super P> condition) {
		if (!selection.isEvery() && !selection.getTypes().contains(type))
			throw new IllegalArgumentException("The tap takes no " + type.getSimpleName() + "; it takes " + describe());

		return packet -> packet.packet(type).filter(condition).isPresent();
	}

	private String describe() {
		return "the packets " + selection.getTypes().stream().map(Class::getSimpleName).sorted().toList()
				+ " and the names " + selection.getNames().stream().sorted().toList();
	}
}
