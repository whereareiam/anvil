package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.game.MessageType;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.packet.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.protocol.mcprotocol.client.TestClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.netty.ReceivedFrames;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FramedClientSessionTest {
	private final TestClientListener<Session> listener = new TestClientListener<>();
	private final Deque<Packet> decoded = new ArrayDeque<>();
	/**
	 * The release's protocol in the game; only connecting moves a real one out of its handshake.
	 */
	private final MinecraftProtocol protocol = new MinecraftProtocol(new GameProfile(new UUID(0, 1), "Alice"), null) {
		@Override
		public SubProtocol getSubProtocol() {
			return SubProtocol.GAME;
		}
	};
	private final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<>() {
		@Override
		protected void initChannel(Channel channel) {
			channel.pipeline().addLast(ReceivedFrames.CODEC, new TestDecoder());
			channel.pipeline().addLast("manager", new FramedClientSession("localhost", 25565, protocol, listener,
					McProtocolClientAdapter.packets(PacketDemand.EVERY)));
		}
	});

	@Test
	void handsEveryTranslatedPacketToTheListenerAndNothingForOthers() {
		receive(new ServerChatPacket(Component.text("anvil:welcome"), MessageType.SYSTEM), 0x0E);
		receive(new ServerKeepAlivePacket(7), 0x1F);

		assertEquals(List.of(new ClientboundSystemChat(Text.literal("anvil:welcome"), false)), listener.received());
		assertEquals(List.of(), listener.frames());
	}

	@Test
	void handsEveryPacketOverAsItsFrameInTheReleasesStateWhileTheListenerWantsFrames() {
		listener.wantFrames(true);

		receive(new ServerChatPacket(Component.text("anvil:welcome"), MessageType.SYSTEM), 0x0E, 1, 2);
		receive(new ServerKeepAlivePacket(7), 0x1F, 3);

		assertEquals(List.of(
				new TestClientListener.Frame(ConnectionState.PLAY, 0x0E, new byte[]{1, 2},
						new ClientboundSystemChat(Text.literal("anvil:welcome"), false)),
				new TestClientListener.Frame(ConnectionState.PLAY, 0x1F, new byte[]{3}, null)
		), listener.frames());
		assertEquals(List.of(), listener.received());
	}

	/**
	 * Writes a frame of the packet's ID and payload, which the test's decoder decodes to the packet.
	 */
	private void receive(Packet packet, int id, int... payload) {
		ByteBuf frame = Unpooled.buffer();
		frame.writeByte(id);
		for (int value : payload) frame.writeByte(value);
		decoded.add(packet);
		channel.writeInbound(frame);
	}

	/**
	 * Decodes each frame to the next packet the test wrote, in place of the release's decoder.
	 */
	private final class TestDecoder extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext context, Object message) {
			ReferenceCountUtil.release(message);
			context.fireChannelRead(decoded.remove());
		}
	}
}
