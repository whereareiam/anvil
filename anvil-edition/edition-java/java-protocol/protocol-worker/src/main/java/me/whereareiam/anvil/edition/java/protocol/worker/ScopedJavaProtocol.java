package me.whereareiam.anvil.edition.java.protocol.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.LocalPlayer;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.player.api.channel.Subscription;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A {@link JavaPacketDispatcher} as one extension sees it while binding: the subscriptions it creates, the ticks of its
 * client's player among them, are reported to its scope.
 */
@RequiredArgsConstructor
final class ScopedJavaProtocol implements JavaProtocol {
	private final JavaPacketDispatcher protocol;
	private final LocalPlayer player;
	private final Consumer<Subscription> track;

	@Override
	public <P extends ClientboundPacket> @NotNull Subscription listen(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return tracked(protocol.listen(type, listener));
	}

	@Override
	public @NotNull Subscription listenFrames(@NotNull Consumer<? super ClientboundFrame> listener) {
		return tracked(protocol.listenFrames(listener));
	}

	@Override
	public @NotNull Subscription want(@NotNull Class<? extends ClientboundPacket> type) {
		return tracked(protocol.want(type));
	}

	@Override
	public boolean handsOverFrames() {
		return protocol.handsOverFrames();
	}

	@Override
	public void send(@NotNull ServerboundPacket packet) {
		protocol.send(packet);
	}

	@Override
	public <P extends ServerboundPacket> @NotNull Subscription listenSent(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return tracked(protocol.listenSent(type, listener));
	}

	@Override
	public boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return protocol.supports(type);
	}

	@Override
	public int protocolNumber() {
		return protocol.protocolNumber();
	}

	@Override
	public @NotNull JavaWorld world() {
		return protocol.world();
	}

	@Override
	public @NotNull LocalPlayer player() {
		return player;
	}

	private @NotNull Subscription tracked(@NotNull Subscription subscription) {
		track.accept(subscription);
		return subscription;
	}
}
