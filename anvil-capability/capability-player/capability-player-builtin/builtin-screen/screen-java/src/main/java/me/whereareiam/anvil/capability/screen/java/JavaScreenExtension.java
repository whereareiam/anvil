package me.whereareiam.anvil.capability.screen.java;

import me.whereareiam.anvil.capability.screen.channel.ScreenChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Installs the built-in screen capability for players that speak the Java Edition protocol: it emits each title the
 * player's current connection shows, through {@link ClientTitles}, each action bar it receives, whether through the
 * action-bar packet or as a system message shown as an overlay, and each change of its boss bars, through
 * {@link ClientBossBars}. Each connection starts without a title and without boss bars, and the
 * bars still shown when it ends are emitted as removed. The screen registers no operations.
 */
public final class JavaScreenExtension implements WorkerExtension<Object> {
	private final LongSupplier nanoClock;

	/**
	 * Creates the extension, timing titles on the system clock.
	 */
	public JavaScreenExtension() {
		this(System::nanoTime);
	}

	JavaScreenExtension(@NotNull LongSupplier nanoClock) {
		this.nanoClock = nanoClock;
	}

	@Override
	public @NotNull String id() {
		return ScreenChannel.ID;
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
						player.emit(ScreenChannel.ACTION_BAR, packet.getText())),
				protocol.listen(ClientboundSystemChat.class, packet -> {
					if (packet.isOverlay()) player.emit(ScreenChannel.ACTION_BAR, packet.getText());
				}),
				protocol.listen(ClientboundBossEvent.class, bossBars::apply),
				bossBars::discard,
				connections
		);

		return binding::close;
	}
}
