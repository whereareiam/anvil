package me.whereareiam.anvil.capability.messages.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputMessage;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.jetbrains.annotations.NotNull;
import org.jose4j.json.JsonUtil;
import org.jose4j.lang.JoseException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Messages packets of the CloudburstMC Protocol release for every Bedrock codec it has. Chat is a text packet of the
 * chat type named after the profile the login claimed; commands are command requests from the player, carrying the
 * leading slash a Bedrock client sends. The packets' codec, chosen per player by its protocol, writes the fields its
 * protocol has.
 *
 * <p>A received message is any text packet, whatever its type, including the tip and popup texts a client shows
 * above the hotbar, and each message of a command output, which a Bedrock client prints into its chat. Plain text
 * drops the {@code §} formatting codes. A message of the chat, whisper or announcement type is its text without the
 * author; a translated message is its key, without the {@code %} that marks it, followed by each parameter after a
 * space, as is each command output message; a JSON message is its raw text flattened like a translated component:
 * text parts, the key and then each argument of translated parts, and selector patterns, while score parts contribute
 * nothing.</p>
 */
public final class CloudburstMessagesPackets implements MessagesPackets<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> {
	@SuppressWarnings("unchecked")
	private static final Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> SESSION =
			(Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>>) (Class<?>) CloudburstSession.class;
	/**
	 * Command version that codecs before protocol 898 write as a number. CloudburstMC Protocol reads the
	 * {@code latest} version that newer codecs write as this number.
	 */
	private static final int COMMAND_VERSION = 48;
	private static final Pattern FORMATTING = Pattern.compile("§.?");

	@Override
	public @NotNull Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> sessionType() {
		return SESSION;
	}

	@Override
	public void chat(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, @NotNull String text) {
		TextPacket packet = new TextPacket();
		packet.setType(TextPacket.Type.CHAT);
		packet.setNeedsTranslation(false);
		packet.setSourceName(session.profile().getName());
		packet.setMessage(text);
		packet.setXuid("");
		session.send(packet);
	}

	@Override
	public void command(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, @NotNull String commandWithoutSlash) {
		GameStart start = session.start()
				.orElseThrow(() -> new IllegalStateException("A Bedrock player sends commands only after its game started"));

		CommandRequestPacket packet = new CommandRequestPacket();
		packet.setCommand("/" + commandWithoutSlash);
		packet.setCommandOriginData(new CommandOriginData(CommandOriginType.PLAYER, UUID.randomUUID(), "", start.getUniqueEntityId()));
		packet.setInternal(false);
		packet.setVersion(COMMAND_VERSION);
		session.send(packet);
	}

	@Override
	public @NotNull Runnable listen(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, @NotNull Consumer<String> plainText) {
		PacketSubscription texts = session.subscribe(TextPacket.class, packet -> plainText.accept(text(packet)));
		PacketSubscription outputs = session.subscribe(CommandOutputPacket.class, packet -> {
			for (CommandOutputMessage message : packet.getMessages())
				plainText.accept(translated(message.getMessageId(), Arrays.asList(message.getParameters())));
		});
		return () -> {
			texts.close();
			outputs.close();
		};
	}

	private static String text(TextPacket packet) {
		return switch (packet.getType()) {
			case TRANSLATION, POPUP, JUKEBOX_POPUP -> translated(packet.getMessage(), packet.getParameters());
			case JSON, WHISPER_JSON, ANNOUNCEMENT_JSON -> json(packet.getMessage());
			default -> plain(packet.getMessage());
		};
	}

	private static String translated(String key, List<String> parameters) {
		String plainKey = plain(key);
		StringBuilder text = new StringBuilder(plainKey.startsWith("%") ? plainKey.substring(1) : plainKey);
		for (String parameter : parameters)
			text.append(' ').append(plain(parameter));

		return text.toString();
	}

	private static String plain(String text) {
		return FORMATTING.matcher(text).replaceAll("");
	}

	/**
	 * Flattens a JSON raw text message; text that is not a raw text object stays as it arrived, without formatting.
	 */
	private static String json(String message) {
		Map<String, Object> rawText;
		try {
			rawText = JsonUtil.parseJson(message);
		} catch (JoseException | RuntimeException notJson) {
			return plain(message);
		}

		StringBuilder text = new StringBuilder();
		appendRawText(rawText, text);
		return text.toString();
	}

	private static void appendRawText(Object value, StringBuilder text) {
		if (value instanceof String argument) {
			text.append(plain(argument));
			return;
		}
		if (value instanceof List<?> parts) {
			parts.forEach(part -> appendRawText(part, text));
			return;
		}
		if (!(value instanceof Map<?, ?> part)) return;

		if (part.get("rawtext") != null) appendRawText(part.get("rawtext"), text);
		if (part.get("text") instanceof String content) text.append(plain(content));
		if (part.get("selector") instanceof String selector) text.append(selector);
		if (part.get("translate") instanceof String key) {
			text.append(plain(key));
			appendArguments(part.get("with"), text);
		}
	}

	/**
	 * Appends each argument of a translated part after a space: a list of strings, or raw text whose parts are the
	 * arguments.
	 */
	private static void appendArguments(Object arguments, StringBuilder text) {
		Object list = arguments instanceof Map<?, ?> rawText ? rawText.get("rawtext") : arguments;
		if (!(list instanceof List<?> values)) return;

		for (Object argument : values) {
			text.append(' ');
			appendRawText(argument, text);
		}
	}
}
