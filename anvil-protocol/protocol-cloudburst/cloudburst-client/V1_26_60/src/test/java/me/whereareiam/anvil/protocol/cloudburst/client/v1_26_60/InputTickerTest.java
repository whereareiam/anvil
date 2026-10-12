package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputTickerTest {
	private final PositionTracker positions = new PositionTracker();
	private final List<BedrockPacket> sent = new ArrayList<>();
	private long tick = 100;
	private boolean serverMovement = true;
	private final InputTicker ticker = new InputTicker(positions, () -> tick, () -> serverMovement, this::send);

	@BeforeEach
	void start() {
		StartGamePacket start = new StartGamePacket();
		start.setRuntimeEntityId(7);
		start.setPlayerPosition(Vector3f.from(1, 2, 3));
		start.setRotation(Vector2f.from(10, 20));
		positions.received(start);
		ticker.received(start);
	}

	@Test
	void repeatsTheTrackedPositionAndRotationOnEveryTickUnderServerAuthority() {
		ticker.tick();
		tick++;
		ticker.tick();

		assertEquals(2, sent.size());
		PlayerAuthInputPacket first = input(0);
		assertEquals(Vector3f.from(1, 2, 3), first.getPosition());
		assertEquals(Vector3f.from(10, 20, 20), first.getRotation());
		assertEquals(Vector2f.from(10, 20), first.getInteractRotation());
		assertEquals(100, first.getTick());
		assertEquals(Set.of(), first.getInputData());
		assertEquals(101, input(1).getTick());
	}

	@Test
	void sendsNothingUnderClientAuthority() {
		serverMovement = false;

		ticker.tick();

		assertEquals(List.of(), sent);
	}

	@Test
	void leavesATickToAnInputTheClientAlreadySentAndFollowsItsPosition() {
		PlayerAuthInputPacket move = new PlayerAuthInputPacket();
		move.setPosition(Vector3f.from(4, 5, 6));
		move.setRotation(Vector3f.from(1, 2, 2));
		move.setTick(tick);
		send(move);

		ticker.tick();
		assertEquals(1, sent.size());

		tick++;
		ticker.tick();
		assertEquals(Vector3f.from(4, 5, 6), input(1).getPosition());
		assertEquals(Vector3f.from(1, 2, 2), input(1).getRotation());
	}

	@Test
	void reportsOnceThatItHandledATeleportOfItsOwnEntity() {
		ticker.received(move(8, MovePlayerPacket.Mode.TELEPORT));
		ticker.received(move(7, MovePlayerPacket.Mode.NORMAL));
		ticker.tick();
		assertEquals(Set.of(), input(0).getInputData());

		MovePlayerPacket teleport = move(7, MovePlayerPacket.Mode.TELEPORT);
		positions.received(teleport);
		ticker.received(teleport);
		tick++;
		ticker.tick();
		tick++;
		ticker.tick();

		assertEquals(Vector3f.from(9, 9, 9), input(1).getPosition());
		assertTrue(input(1).getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT));
		assertEquals(Set.of(), input(2).getInputData());
	}

	private void send(BedrockPacket packet) {
		positions.sent(packet);
		ticker.sent(packet);
		sent.add(packet);
	}

	private PlayerAuthInputPacket input(int index) {
		return assertInstanceOf(PlayerAuthInputPacket.class, sent.get(index));
	}

	private static MovePlayerPacket move(long runtimeEntityId, MovePlayerPacket.Mode mode) {
		MovePlayerPacket move = new MovePlayerPacket();
		move.setRuntimeEntityId(runtimeEntityId);
		move.setMode(mode);
		move.setPosition(Vector3f.from(9, 9, 9));
		move.setRotation(Vector3f.ZERO);
		return move;
	}
}
