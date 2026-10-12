package me.whereareiam.anvil.capability.messages.cloudburst.v1_26_60;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v1001.Bedrock_v1001;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.codec.v975.Bedrock_v975;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputMessage;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudburstMessagesPacketsTest {
	private static final List<BedrockCodec> CODECS = List.of(
			Bedrock_v589.CODEC, Bedrock_v594.CODEC, Bedrock_v618.CODEC, Bedrock_v622.CODEC, Bedrock_v630.CODEC,
			Bedrock_v649.CODEC, Bedrock_v662.CODEC, Bedrock_v671.CODEC, Bedrock_v685.CODEC, Bedrock_v686.CODEC,
			Bedrock_v712.CODEC, Bedrock_v729.CODEC, Bedrock_v748.CODEC, Bedrock_v766.CODEC, Bedrock_v776.CODEC,
			Bedrock_v786.CODEC, Bedrock_v800.CODEC, Bedrock_v818.CODEC, Bedrock_v819.CODEC, Bedrock_v827.CODEC,
			Bedrock_v844.CODEC, Bedrock_v859.CODEC, Bedrock_v860.CODEC, Bedrock_v898.CODEC, Bedrock_v924.CODEC,
			Bedrock_v944.CODEC, Bedrock_v975.CODEC, Bedrock_v1001.CODEC, Bedrock_v2168.CODEC, Bedrock_v2169.CODEC,
			Bedrock_v2193.CODEC, Bedrock_v2225.CODEC
	);

	private final CloudburstMessagesPackets packets = new CloudburstMessagesPackets();

	@Test
	void isTheSegmentsMessagesPortForCloudburstSessions() {
		MessagesPackets<?> port = ServiceLoader.load(MessagesPackets.class).findFirst().orElseThrow();

		assertInstanceOf(CloudburstMessagesPackets.class, port);
		assertEquals(CloudburstSession.class, port.sessionType());
	}

	@Test
	void sendsChatAsATextPacketOfTheClaimedProfileThatEveryCodecWrites() throws Exception {
		for (BedrockCodec codec : CODECS) {
			RecordingSession session = new RecordingSession();
			packets.chat(session, "hello there");

			TextPacket chat = roundTrip(codec, assertInstanceOf(TextPacket.class, session.sent.getFirst()));
			assertEquals(TextPacket.Type.CHAT, chat.getType(), codec.getMinecraftVersion());
			assertEquals("Alice", chat.getSourceName());
			assertEquals("hello there", chat.getMessage());
			assertFalse(chat.isNeedsTranslation());
		}
	}

	@Test
	void sendsCommandsAsPlayerCommandRequestsWithTheirSlashThatEveryCodecWrites() throws Exception {
		for (BedrockCodec codec : CODECS) {
			RecordingSession session = new RecordingSession();
			session.start = GameStart.builder().runtimeEntityId(7).uniqueEntityId(70).build();
			packets.command(session, "say ping");

			CommandRequestPacket request = roundTrip(codec, assertInstanceOf(CommandRequestPacket.class, session.sent.getFirst()));
			assertEquals("/say ping", request.getCommand(), codec.getMinecraftVersion());
			CommandOriginData origin = request.getCommandOriginData();
			assertEquals(CommandOriginType.PLAYER, origin.getOrigin());
			assertEquals("", origin.getRequestId());
			assertFalse(request.isInternal());
		}
	}

	@Test
	void refusesACommandBeforeTheGameStarted() {
		assertThrows(IllegalStateException.class, () -> packets.command(new RecordingSession(), "list"));
	}

	@Test
	void flattensEveryTextTypeAndEachCommandOutputMessageAsEveryCodecReadsThem() throws Exception {
		for (BedrockCodec codec : CODECS) {
			RecordingSession session = new RecordingSession();
			List<String> received = new ArrayList<>();
			packets.listen(session, received::add);

			session.receive(roundTrip(codec, text(TextPacket.Type.RAW, "§aanvil:§r raw", List.of())));
			session.receive(roundTrip(codec, chat("Bob", "hello")));
			session.receive(roundTrip(codec, text(TextPacket.Type.TRANSLATION, "§e%multiplayer.player.joined", List.of("Alice"))));
			session.receive(roundTrip(codec, text(TextPacket.Type.TRANSLATION, "chat.type.text", List.of("Alice", "hi"))));
			session.receive(roundTrip(codec, text(TextPacket.Type.TIP, "tip", List.of())));
			session.receive(roundTrip(codec, text(TextPacket.Type.POPUP, "popup", List.of())));
			session.receive(roundTrip(codec, text(TextPacket.Type.SYSTEM, "system", List.of())));
			session.receive(roundTrip(codec, text(TextPacket.Type.JSON,
					"{\"rawtext\":[{\"text\":\"§bjson \"},{\"translate\":\"commands.give.success\",\"with\":[\"Stone\",\"1\"]},"
							+ "{\"text\":\" \"},{\"translate\":\"key\",\"with\":{\"rawtext\":[{\"selector\":\"@p\"}]}},"
							+ "{\"score\":{\"name\":\"@p\",\"objective\":\"o\"}}]}",
					List.of())));
			session.receive(roundTrip(codec, output()));

			assertEquals(List.of(
					"anvil: raw",
					"hello",
					"multiplayer.player.joined Alice",
					"chat.type.text Alice hi",
					"tip",
					"popup",
					"system",
					"json commands.give.success Stone 1 key @p",
					"commands.op.success Alice",
					"commands.generic.unknown nope"
			), received, codec.getMinecraftVersion());
		}
	}

	@Test
	void stopsReportingOnceTheHandleRan() {
		RecordingSession session = new RecordingSession();
		List<String> received = new ArrayList<>();
		Runnable detach = packets.listen(session, received::add);

		detach.run();
		detach.run();
		session.receive(text(TextPacket.Type.RAW, "late", List.of()));

		assertTrue(received.isEmpty());
		assertTrue(session.subscriptions.isEmpty());
	}

	private static TextPacket chat(String source, String message) {
		TextPacket packet = text(TextPacket.Type.CHAT, message, List.of());
		packet.setSourceName(source);
		return packet;
	}

	private static TextPacket text(TextPacket.Type type, String message, List<String> parameters) {
		TextPacket packet = new TextPacket();
		packet.setType(type);
		packet.setSourceName("");
		packet.setMessage(message);
		packet.setParameters(new ArrayList<>(parameters));
		packet.setXuid("");
		return packet;
	}

	private static CommandOutputPacket output() {
		CommandOutputPacket packet = new CommandOutputPacket();
		packet.setCommandOriginData(new CommandOriginData(CommandOriginType.PLAYER, new UUID(0, 1), "", 0));
		packet.setType(CommandOutputType.ALL_OUTPUT);
		packet.getMessages().add(new CommandOutputMessage(false, "commands.op.success", new String[]{"Alice"}));
		packet.getMessages().add(new CommandOutputMessage(false, "§c%commands.generic.unknown", new String[]{"nope"}));
		return packet;
	}

	/**
	 * Writes a packet with a codec and reads it back, as the codec's peer does.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends BedrockPacket> T roundTrip(BedrockCodec codec, T packet) throws Exception {
		BedrockCodecHelper helper = codec.createHelper();
		ByteBuf buffer = Unpooled.buffer();
		try {
			codec.tryEncode(helper, buffer, packet);
			int id = codec.getPacketDefinition(packet.getClass()).getId();
			return (T) codec.tryDecode(helper, buffer, id);
		} finally {
			buffer.release();
		}
	}

	/**
	 * A session that records what is sent and delivers received packets to its subscriptions.
	 */
	private static final class RecordingSession implements CloudburstSession<BedrockPacket, ItemDefinition> {
		private final List<BedrockPacket> sent = new ArrayList<>();
		private final List<Subscription<?>> subscriptions = new ArrayList<>();
		private GameStart start;

		@Override
		public int protocol() {
			return Bedrock_v2193.CODEC.getProtocolVersion();
		}

		@Override
		public boolean connected() {
			return true;
		}

		@Override
		public @NotNull ClientProfile profile() {
			return ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build();
		}

		@Override
		public void send(@NotNull BedrockPacket packet) {
			sent.add(packet);
		}

		@Override
		public <T extends BedrockPacket> @NotNull PacketSubscription subscribe(@NotNull Class<T> type, @NotNull Consumer<? super T> listener) {
			Subscription<T> subscription = new Subscription<>(type, listener);
			subscriptions.add(subscription);
			return () -> subscriptions.remove(subscription);
		}

		@Override
		public @NotNull Optional<GameStart> start() {
			return Optional.ofNullable(start);
		}

		@Override
		public boolean serverAuthoritativeMovement() {
			return true;
		}

		@Override
		public @NotNull List<ItemDefinition> itemDefinitions() {
			return List.of();
		}

		@Override
		public long tick() {
			return 0;
		}

		private void receive(BedrockPacket packet) {
			List.copyOf(subscriptions).forEach(subscription -> subscription.deliver(packet));
		}
	}

	/**
	 * One listener of the packets of a type.
	 */
	private record Subscription<T extends BedrockPacket>(Class<T> type, Consumer<? super T> listener) {
		private void deliver(BedrockPacket packet) {
			if (type.isInstance(packet)) listener.accept(type.cast(packet));
		}
	}
}
