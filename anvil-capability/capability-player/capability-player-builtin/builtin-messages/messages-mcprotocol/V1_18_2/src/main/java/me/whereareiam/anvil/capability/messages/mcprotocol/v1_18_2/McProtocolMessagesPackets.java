package me.whereareiam.anvil.capability.messages.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Messages packets of the MCProtocolLib release for Minecraft 1.18.2, the first one with serverbound and clientbound
 * packet names. It still has one unsigned chat packet in each direction and no command packet, so commands are chat
 * with a leading slash. Listeners follow packetlib 2.1 and components are adventure 4.9, which this release ships
 * without a plain-text serializer.
 */
public final class McProtocolMessagesPackets implements MessagesPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void chat(@NotNull Session session, @NotNull String text) {
		session.send(new ServerboundChatPacket(text));
	}

	@Override
	public void command(@NotNull Session session, @NotNull String commandWithoutSlash) {
		session.send(new ServerboundChatPacket("/" + commandWithoutSlash));
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull Consumer<String> plainText) {
		SessionListener listener = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (session.isConnected() && packet instanceof ClientboundChatPacket chat) plainText.accept(flatten(chat.getMessage()));
			}
		};
		session.addListener(listener);
		return () -> session.removeListener(listener);
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
			for (Component argument : value.args()) {
				text.append(' ');
				append(argument, text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
