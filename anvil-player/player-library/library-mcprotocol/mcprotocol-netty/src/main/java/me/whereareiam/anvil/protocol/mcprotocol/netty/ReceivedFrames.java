package me.whereareiam.anvil.protocol.mcprotocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Hands each packet one MCProtocolLib session receives to its {@link ClientListener}, translated, in the order the
 * packets arrive on the wire, and while the listener wants frames as its frame. Every release's session decodes in a
 * netty pipeline that names its decoder {@value #CODEC}; this class puts two handlers around it, before any packet
 * arrives: one before the decoder that copies a frame's bytes while frames are wanted, and one after it that
 * translates the packet decoded from them and delivers both. Releases hand decoded packets to session listeners on
 * other threads or out of order, so translation and delivery happen here, on the channel's own thread, once per packet.
 *
 * <p>A release that inserts its decompression before the decoder once the server enables compression would put it
 * between the frame handler and the decoder; after each packet the delivering handler moves the frame handler back next
 * to the decoder, so frames are always decompressed. A packet the decoder decodes from nothing, which no release does,
 * would be delivered as no frame.</p>
 *
 * <pre>{@code
 * ReceivedFrames<Session, Packet> frames = new ReceivedFrames<>(session, Packet.class, packets, listener, state);
 * frames.install(channel.pipeline()); // when the session's channel becomes active
 * }</pre>
 *
 * @param <S> native session type of the release
 * @param <P> packet type of the release
 */
public final class ReceivedFrames<S, P> {
	/**
	 * The name every release gives its packet decoder in the session's pipeline.
	 */
	public static final String CODEC = "codec";
	/**
	 * The name of the handler that copies frames.
	 */
	public static final String FRAMES = "anvil-frames";
	/**
	 * The name of the handler that translates and delivers packets.
	 */
	public static final String DELIVERY = "anvil-delivery";

	private final @NotNull S session;
	private final @NotNull Class<P> packetType;
	private final @NotNull PacketTable<P> packets;
	private final @NotNull ClientListener<? super S> listener;
	private final @NotNull Supplier<@Nullable ConnectionState> state;
	/**
	 * The frame whose bytes the decoder is decoding, while frames are wanted; only the channel's thread touches it.
	 */
	private @Nullable Frame pending;

	/**
	 * Creates the handlers of one session.
	 *
	 * @param session the session whose packets they hand over
	 * @param packetType the release's packet type, which the decoder emits
	 * @param packets the session's translations
	 * @param listener the session's listener
	 * @param state the connection's state as the decoder reads the next packet, or {@code null} in a state no player's
	 * packets arrive in, such as the server list's
	 */
	public ReceivedFrames(
			@NotNull S session,
			@NotNull Class<P> packetType,
			@NotNull PacketTable<P> packets,
			@NotNull ClientListener<? super S> listener,
			@NotNull Supplier<@Nullable ConnectionState> state
	) {
		this.session = session;
		this.packetType = packetType;
		this.packets = packets;
		this.listener = listener;
		this.state = state;
	}

	/**
	 * Puts the handlers around the decoder of the session's pipeline.
	 *
	 * @param pipeline the session's channel pipeline
	 * @throws IllegalStateException when the pipeline has no decoder named {@value #CODEC}
	 */
	public void install(@NotNull ChannelPipeline pipeline) {
		if (pipeline.get(CODEC) == null)
			throw new IllegalStateException("The session's pipeline has no " + CODEC + " handler; it holds " + pipeline.names());

		pipeline.addBefore(CODEC, FRAMES, new FrameCopy());
		pipeline.addAfter(CODEC, DELIVERY, new Delivery());
	}

	/**
	 * Copies a frame's bytes while the listener wants frames, before the decoder reads them, and delivers a frame the
	 * decoder decoded no packet from.
	 */
	private final class FrameCopy extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext context, Object message) {
			ConnectionState current = state.get();
			if (!(message instanceof ByteBuf bytes) || current == null || !listener.wantsFrames()) {
				context.fireChannelRead(message);
				return;
			}

			pending = Frame.read(current, bytes);
			try {
				context.fireChannelRead(message);
			} finally {
				Frame undecoded = pending;
				pending = null;
				if (undecoded != null)
					listener.received(session, undecoded.state(), undecoded.id(), undecoded.payload(), null);
			}
		}
	}

	/**
	 * Translates each decoded packet and delivers it, with its frame when one was copied, then lets the session have it.
	 */
	private final class Delivery extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext context, Object message) {
			if (!packetType.isInstance(message)) {
				context.fireChannelRead(message);
				return;
			}

			ClientboundPacket translated = packets.decode(packetType.cast(message));
			Frame frame = pending;
			pending = null;
			if (frame != null) listener.received(session, frame.state(), frame.id(), frame.payload(), translated);
			else if (translated != null) listener.received(session, translated);

			context.fireChannelRead(message);
			keepFramesBeforeDecoder(context.pipeline());
		}

		/**
		 * Moves the frame handler back next to the decoder when the session put a handler, such as its decompression,
		 * between them while it handled the packet.
		 */
		private void keepFramesBeforeDecoder(ChannelPipeline pipeline) {
			List<String> names = pipeline.names();
			int frames = names.indexOf(FRAMES);
			int codec = names.indexOf(CODEC);
			if (frames < 0 || codec < 0 || codec == frames + 1) return;

			pipeline.remove(FRAMES);
			pipeline.addBefore(CODEC, FRAMES, new FrameCopy());
		}
	}

	/**
	 * One frame's state, ID and the bytes after its ID.
	 */
	private record Frame(ConnectionState state, int id, byte[] payload) {
		/**
		 * Reads a frame without moving the buffer's reader index, which the decoder reads from.
		 */
		static Frame read(ConnectionState state, ByteBuf bytes) {
			ByteBuf view = bytes.duplicate();
			int id = readVarInt(view);
			byte[] payload = new byte[view.readableBytes()];
			view.readBytes(payload);

			return new Frame(state, id, payload);
		}

		private static int readVarInt(ByteBuf bytes) {
			int value = 0;
			for (int position = 0; position < 35; position += 7) {
				byte next = bytes.readByte();
				value |= (next & 0x7F) << position;
				if ((next & 0x80) == 0) return value;
			}

			throw new IllegalStateException("A frame's packet ID is longer than a VarInt");
		}
	}
}
