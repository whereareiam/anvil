package me.whereareiam.anvil.capability.overlays.java;

import me.whereareiam.anvil.capability.overlays.channel.OverlaysChannel;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundBossEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundClearTitles;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetActionBarText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetSubtitleText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetTitleText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Installs the built-in overlays capability for players that speak the Java Edition protocol: it emits each title the
 * player's current connection shows, through {@link ClientTitles}, each action bar it receives, whether through the
 * action-bar packet or as a system message shown as an overlay, and each change of its boss bars, through
 * {@link ClientBossBars}. Each connection starts without a title and without boss bars, and the
 * bars still shown when it ends are emitted as removed. The overlays extension registers no operations.
 */
public final class JavaOverlaysExtension implements WorkerExtension<Object> {
	private final LongSupplier nanoClock;

	/**
	 * Creates the extension, timing titles on the system clock.
	 */
	public JavaOverlaysExtension() {
		this(System::nanoTime);
	}

	JavaOverlaysExtension(@NotNull LongSupplier nanoClock) {
		this.nanoClock = nanoClock;
	}

	@Override
	public @NotNull String id() {
		return OverlaysChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		AtomicReference<ClientTitles> titles = new AtomicReference<>(new ClientTitles(player, nanoClock));
		ClientBossBars bossBars = new ClientBossBars(player);

		Subscription connections = player.bindConnection(() -> {
			titles.set(new ClientTitles(player, nanoClock));
			return bossBars::removeAll;
		});
		Subscription binding = Subscription.all(
				protocol.listen(ClientboundSetTitleText.class, packet -> titles.get().title(packet)),
				protocol.listen(ClientboundSetSubtitleText.class, packet -> titles.get().subtitle(packet)),
				protocol.listen(ClientboundSetTitlesAnimation.class, packet -> titles.get().times(packet)),
				protocol.listen(ClientboundClearTitles.class, packet -> titles.get().cleared(packet)),
				protocol.listen(ClientboundSetActionBarText.class, packet ->
						player.emit(OverlaysChannel.ACTION_BAR, packet.getText())),
				protocol.listen(ClientboundSystemChat.class, packet -> {
					if (packet.isOverlay()) player.emit(OverlaysChannel.ACTION_BAR, packet.getText().plain());
				}),
				protocol.listen(ClientboundBossEvent.class, bossBars::apply),
				bossBars::discard,
				connections
		);

		return binding::close;
	}
}
