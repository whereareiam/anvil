package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

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
import me.whereareiam.anvil.player.library.mcprotocol.client.TestClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.netty.ReceivedFrames;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FramedClientSessionTest {
	private static final ClientLogin LOGIN = ClientLogin.builder()
			.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
			.minecraftVersion("1.20.6")
			.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
			.connection(ClientConnection.builder().host("localhost").port(25565).build())
			.build();

	private final TestClientListener<Session> listener = new TestClientListener<>();
	private final Deque<Packet> decoded = new ArrayDeque<>();
	private final ClientProtocol protocol = new ClientProtocol(LOGIN);
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
		receive(new ClientboundSystemChatPacket(Component.text("anvil:welcome"), false), 0x0E);
		receive(new ClientboundKeepAlivePacket(7), 0x1F);

		assertEquals(List.of(new ClientboundSystemChat(Text.literal("anvil:welcome"), false)), listener.received());
		assertEquals(List.of(), listener.frames());
	}

	@Test
	void handsEveryPacketOverAsItsFrameInTheReleasesStateWhileTheListenerWantsFrames() {
		protocol.setState(ProtocolState.GAME);
		listener.wantFrames(true);

		receive(new ClientboundSystemChatPacket(Component.text("anvil:welcome"), false), 0x0E, 1, 2);
		receive(new ClientboundKeepAlivePacket(7), 0x1F, 3);

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
