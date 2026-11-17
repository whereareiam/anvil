package me.whereareiam.anvil.protocol.mcprotocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.mcprotocol.client.TestClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivedFramesTest {
	private static final int CHAT = 0x77;
	private static final int COMPRESS = 0x03;
	private static final byte COMPRESSED = (byte) 0xC0;

	private final TestClientListener<String> listener = new TestClientListener<>();
	private final List<Integer> handled = new ArrayList<>();
	private final EmbeddedChannel channel = new EmbeddedChannel();

	ReceivedFramesTest() {
		channel.pipeline().addLast(ReceivedFrames.CODEC, new TestDecoder());
		channel.pipeline().addLast("manager", new TestSession());
		new ReceivedFrames<>("session", TestPacket.class, new PacketTable<>(List.of(new ChatTranslation())), listener,
				() -> ConnectionState.PLAY).install(channel.pipeline());
	}

	@Test
	void deliversTranslatedPacketsAloneWhileNoFramesAreWanted() {
		channel.writeInbound(frame(0x08, 1, 2), frame(CHAT, 'h', 'i'));

		assertEquals(List.of(chat("hi")), listener.received());
		assertEquals(List.of(), listener.frames());
		assertEquals(List.of(0x08, CHAT), handled);
	}

	@Test
	void deliversEveryFrameWithItsTranslationInArrivalOrder() {
		listener.wantFrames(true);

		channel.writeInbound(frame(0x08, 1, 2), frame(CHAT, 'h', 'i'));

		assertEquals(List.of(
				new TestClientListener.Frame(ConnectionState.PLAY, 0x08, new byte[]{1, 2}, null),
				new TestClientListener.Frame(ConnectionState.PLAY, CHAT, new byte[]{'h', 'i'}, chat("hi"))
		), listener.frames());
		assertEquals(List.of(), listener.received());
		assertEquals(List.of(0x08, CHAT), handled);
	}

	@Test
	void copiesFramesAfterADecompressionTheSessionInsertsBeforeTheDecoder() {
		listener.wantFrames(true);

		channel.writeInbound(frame(COMPRESS), compressed(frame(0x08, 7)));

		assertEquals(List.of(
				new TestClientListener.Frame(ConnectionState.PLAY, COMPRESS, new byte[0], null),
				new TestClientListener.Frame(ConnectionState.PLAY, 0x08, new byte[]{7}, null)
		), listener.frames());
		assertEquals(List.of("anvil-frames", "codec", "anvil-delivery"),
				channel.pipeline().names().subList(1, 4));
	}

	@Test
	void refusesAPipelineWithoutDecoder() {
		EmbeddedChannel bare = new EmbeddedChannel();
		ReceivedFrames<String, TestPacket> frames = new ReceivedFrames<>("session", TestPacket.class,
				new PacketTable<>(List.of()), listener, () -> ConnectionState.PLAY);

		assertThrows(IllegalStateException.class, () -> frames.install(bare.pipeline()));
	}

	private static ByteBuf frame(int id, int... payload) {
		ByteBuf bytes = Unpooled.buffer();
		bytes.writeByte(id);
		for (int value : payload) bytes.writeByte(value);
		return bytes;
	}

	private static ClientboundSystemChat chat(String text) {
		return new ClientboundSystemChat(Text.literal(text), false);
	}

	private static ByteBuf compressed(ByteBuf frame) {
		return Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(new byte[]{COMPRESSED}), frame);
	}

	/**
	 * A decoded packet of the test release: its ID and the bytes after it.
	 */
	private record TestPacket(int id, byte[] payload) {
	}

	/**
	 * Decodes a frame into a packet, as a release's decoder does.
	 */
	private static final class TestDecoder extends ByteToMessageDecoder {
		@Override
		protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
			int id = in.readByte();
			byte[] payload = new byte[in.readableBytes()];
			in.readBytes(payload);
			out.add(new TestPacket(id, payload));
		}
	}

	/**
	 * Handles packets as a release's session does; the compression packet inserts a decompression before the decoder.
	 */
	private final class TestSession extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext context, Object message) {
			TestPacket packet = (TestPacket) message;
			handled.add(packet.id());
			if (packet.id() == COMPRESS) context.pipeline().addBefore(ReceivedFrames.CODEC, "compression", new Decompression());
		}
	}

	/**
	 * Strips the marker of a compressed frame.
	 */
	private static final class Decompression extends MessageToMessageDecoder<ByteBuf> {
		@Override
		protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
			if (in.readByte() != COMPRESSED) throw new IllegalStateException("The frame is not compressed");
			out.add(in.retain());
		}
	}

	private static final class ChatTranslation implements PacketTranslation<TestPacket> {
		@Override
		public @NotNull Set<Class<? extends JavaPacket>> packets() {
			return Set.of(ClientboundSystemChat.class);
		}

		@Override
		public @Nullable ClientboundPacket decode(@NotNull TestPacket packet) {
			if (packet.id() != CHAT) return null;

			return chat(new String(packet.payload()));
		}

		@Override
		public @Nullable TestPacket encode(@NotNull ServerboundPacket packet) {
			return null;
		}
	}
}
