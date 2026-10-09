package me.whereareiam.anvil.capability.messages.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.BitSet;
import java.util.function.Consumer;

/**
 * Messages packets of the MCProtocolLib release for Minecraft 1.21.1, the first one in the
 * {@code org.geysermc.mcprotocollib} packages and with an unsigned command packet. Chat is still sent unsigned with
 * a timestamp, a salt and nothing acknowledged. Received messages arrive as system chat, player chat, whose plain
 * content the server may replace with an unsigned component, or disguised chat. Components are adventure 4.15, whose
 * translation arguments are {@link TranslationArgument} values.
 */
public final class McProtocolMessagesPackets implements MessagesPackets<Session> {
	private static final int ACKNOWLEDGED_MESSAGES = 20;

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void chat(@NotNull Session session, @NotNull String text) {
		session.send(new ServerboundChatPacket(text, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet(ACKNOWLEDGED_MESSAGES)));
	}

	@Override
	public void command(@NotNull Session session, @NotNull String commandWithoutSlash) {
		session.send(new ServerboundChatCommandPacket(commandWithoutSlash));
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull Consumer<String> plainText) {
		SessionListener listener = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (!session.isConnected()) return;

				String text = messageText(packet);
				if (text != null) plainText.accept(text);
			}
		};
		session.addListener(listener);
		return () -> session.removeListener(listener);
	}

	private static @Nullable String messageText(Packet packet) {
		if (packet instanceof ClientboundSystemChatPacket chat) return flatten(chat.getContent());
		if (packet instanceof ClientboundDisguisedChatPacket chat) return flatten(chat.getMessage());
		if (!(packet instanceof ClientboundPlayerChatPacket chat)) return null;

		Component unsigned = chat.getUnsignedContent();
		return unsigned == null ? chat.getContent() : flatten(unsigned);
	}

	private static String flatten(Component component) {
		StringBuilder text = new StringBuilder();
		append(component, text);
		return text.toString();
	}

	/**
	 * Appends the content of text parts, the key name of keybind parts and the pattern of selector parts and, for a
	 * translated part, its key followed by each argument after a space. Score and NBT parts contribute nothing.
	 */
	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof KeybindComponent value) text.append(value.keybind());
		if (component instanceof SelectorComponent value) text.append(value.pattern());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (TranslationArgument argument : value.arguments()) {
				text.append(' ');
				append(argument.asComponent(), text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
