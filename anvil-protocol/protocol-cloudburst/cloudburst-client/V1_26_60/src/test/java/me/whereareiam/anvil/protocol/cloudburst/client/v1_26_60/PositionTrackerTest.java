package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionTrackerTest {
	private final PositionTracker tracker = new PositionTracker();

	@Test
	void startsAtTheGameStartsPosition() {
		assertThrows(IllegalStateException.class, tracker::position);

		tracker.received(start());

		assertEquals(position(1, 2, 3), tracker.position());
	}

	@Test
	void followsItsOwnMovesAndTheServersCorrectionsOfItsOwnEntityOnly() {
		tracker.received(start());

		PlayerAuthInputPacket input = new PlayerAuthInputPacket();
		input.setPosition(Vector3f.from(4, 5, 6));
		tracker.sent(input);
		assertEquals(position(4, 5, 6), tracker.position());

		tracker.received(move(8, 9, 9, 9));
		assertEquals(position(4, 5, 6), tracker.position());
		tracker.received(move(7, 10, 11, 12));
		assertEquals(position(10, 11, 12), tracker.position());

		CorrectPlayerMovePredictionPacket vehicle = correction(PredictionType.VEHICLE, 0, 0, 0);
		tracker.received(vehicle);
		assertEquals(position(10, 11, 12), tracker.position());
		tracker.received(correction(PredictionType.PLAYER, 13, 14, 15));
		assertEquals(position(13, 14, 15), tracker.position());

		tracker.sent(move(7, 16, 17, 18));
		assertEquals(position(16, 17, 18), tracker.position());
	}

	private static StartGamePacket start() {
		StartGamePacket start = new StartGamePacket();
		start.setRuntimeEntityId(7);
		start.setPlayerPosition(Vector3f.from(1, 2, 3));
		return start;
	}

	private static MovePlayerPacket move(long runtimeEntityId, float x, float y, float z) {
		MovePlayerPacket move = new MovePlayerPacket();
		move.setRuntimeEntityId(runtimeEntityId);
		move.setPosition(Vector3f.from(x, y, z));
		return move;
	}

	private static CorrectPlayerMovePredictionPacket correction(PredictionType type, float x, float y, float z) {
		CorrectPlayerMovePredictionPacket correction = new CorrectPlayerMovePredictionPacket();
		correction.setPredictionType(type);
		correction.setPosition(Vector3f.from(x, y, z));
		return correction;
	}

	private static ClientPosition position(float x, float y, float z) {
		return ClientPosition.builder().x(x).y(y).z(z).build();
	}
}
