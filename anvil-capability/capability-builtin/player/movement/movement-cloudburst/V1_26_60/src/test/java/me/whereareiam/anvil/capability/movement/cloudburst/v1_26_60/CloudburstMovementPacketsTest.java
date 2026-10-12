package me.whereareiam.anvil.capability.movement.cloudburst.v1_26_60;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.math.vector.Vector3f;
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
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudburstMovementPacketsTest {
	private static final List<BedrockCodec> CODECS = List.of(
			Bedrock_v589.CODEC, Bedrock_v594.CODEC, Bedrock_v618.CODEC, Bedrock_v622.CODEC, Bedrock_v630.CODEC,
			Bedrock_v649.CODEC, Bedrock_v662.CODEC, Bedrock_v671.CODEC, Bedrock_v685.CODEC, Bedrock_v686.CODEC,
			Bedrock_v712.CODEC, Bedrock_v729.CODEC, Bedrock_v748.CODEC, Bedrock_v766.CODEC, Bedrock_v776.CODEC,
			Bedrock_v786.CODEC, Bedrock_v800.CODEC, Bedrock_v818.CODEC, Bedrock_v819.CODEC, Bedrock_v827.CODEC,
			Bedrock_v844.CODEC, Bedrock_v859.CODEC, Bedrock_v860.CODEC, Bedrock_v898.CODEC, Bedrock_v924.CODEC,
			Bedrock_v944.CODEC, Bedrock_v975.CODEC, Bedrock_v1001.CODEC, Bedrock_v2168.CODEC, Bedrock_v2169.CODEC,
			Bedrock_v2193.CODEC, Bedrock_v2225.CODEC
	);
	private static final Position POSITION = Position.builder()
			.x(1.5).y(64).z(-2.5)
			.yaw(90).pitch(-15)
			.onGround(true)
			.build();
	private static final float EYES = 64 + 1.62F;

	private final CloudburstMovementPackets packets = new CloudburstMovementPackets();

	@Test
	void isTheSegmentsMovementPortForCloudburstSessions() {
		MovementPackets<?> port = ServiceLoader.load(MovementPackets.class).findFirst().orElseThrow();

		assertInstanceOf(CloudburstMovementPackets.class, port);
		assertEquals(CloudburstSession.class, port.sessionType());
	}

	@Test
	void reportsThePositionAsAuthoritativeInputOfTheCurrentTickThatEveryCodecWrites() throws Exception {
		for (BedrockCodec codec : CODECS) {
			RecordingSession session = new RecordingSession(true);
			packets.move(session, POSITION);

			PlayerAuthInputPacket input = roundTrip(codec, assertInstanceOf(PlayerAuthInputPacket.class, session.sent.getFirst()));
			assertEquals(Vector3f.from(1.5, EYES, -2.5), input.getPosition(), codec.getMinecraftVersion());
			assertEquals(Vector3f.from(-15, 90, 90), input.getRotation());
			assertEquals(1234, input.getTick());
			assertEquals(InputMode.MOUSE, input.getInputMode());
			assertEquals(ClientPlayMode.NORMAL, input.getPlayMode());
			assertTrue(input.getInputData().isEmpty());
		}
	}

	@Test
	void movesThePlayerEntityWithAPlayerMoveUnderClientAuthority() throws Exception {
		for (BedrockCodec codec : CODECS.subList(0, CODECS.indexOf(Bedrock_v818.CODEC))) {
			RecordingSession session = new RecordingSession(false);
			packets.move(session, POSITION);

			MovePlayerPacket move = roundTrip(codec, assertInstanceOf(MovePlayerPacket.class, session.sent.getFirst()));
			assertEquals(7, move.getRuntimeEntityId(), codec.getMinecraftVersion());
			assertEquals(Vector3f.from(1.5, EYES, -2.5), move.getPosition());
			assertEquals(Vector3f.from(-15, 90, 90), move.getRotation());
			assertEquals(MovePlayerPacket.Mode.NORMAL, move.getMode());
			assertTrue(move.isOnGround());
			assertEquals(1234, move.getTick());
		}
	}

	@Test
	void refusesToMoveBeforeTheGameStarted() {
		RecordingSession session = new RecordingSession(true);
		session.start = null;

		assertThrows(IllegalStateException.class, () -> packets.move(session, POSITION));
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
	 * A started session of the given movement authority that records what is sent.
	 */
	private static final class RecordingSession implements CloudburstSession<BedrockPacket, ItemDefinition> {
		private final List<BedrockPacket> sent = new ArrayList<>();
		private final boolean serverMovement;
		private GameStart start = GameStart.builder().runtimeEntityId(7).uniqueEntityId(70).build();

		private RecordingSession(boolean serverMovement) {
			this.serverMovement = serverMovement;
		}

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
			throw new AssertionError("Movement subscribes to nothing");
		}

		@Override
		public @NotNull Optional<GameStart> start() {
			return Optional.ofNullable(start);
		}

		@Override
		public boolean serverAuthoritativeMovement() {
			return serverMovement;
		}

		@Override
		public @NotNull List<ItemDefinition> itemDefinitions() {
			return List.of();
		}

		@Override
		public long tick() {
			return 1234;
		}
	}
}
