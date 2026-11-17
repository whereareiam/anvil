package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerStatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import me.whereareiam.anvil.protocol.java.api.type.player.PlayerCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.PlayerPackets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player translation of the 1.16 chain on the MCProtocolLib 1.17.1 release, whose placement of the player also
 * tells whether it leaves its vehicle and whose player command still carries sneaking.
 */
class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void decodesThePlacementKeepingTheVelocityAlongEachRelativeCoordinate() {
		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(0.5, -32, 0.5)).yaw(0).pitch(10)
						.relatives(Set.of(Relative.Y, Relative.DELTA_Y, Relative.PITCH)).build(),
				packets.decode(new ServerPlayerPositionRotationPacket(0.5, -32, 0.5, 0, 10, 3, true,
						List.of(PositionElement.Y, PositionElement.PITCH))));
	}

	@Test
	void encodesTheMovementWithoutTheHorizontalCollision() {
		ClientPlayerPositionPacket position = assertInstanceOf(ClientPlayerPositionPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, -40, 3)).onGround(true)
						.horizontalCollision(true).build()));
		ClientPlayerRotationPacket rotation = assertInstanceOf(ClientPlayerRotationPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).onGround(true).build()));

		assertEquals(List.of(1.0, -40.0, 3.0), List.of(position.getX(), position.getY(), position.getZ()));
		assertTrue(position.isOnGround());
		assertEquals(List.of(45f, 10f), List.of(rotation.getYaw(), rotation.getPitch()));
		assertTrue(assertInstanceOf(ClientPlayerMovementPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).build())).isOnGround());
	}

	@Test
	void encodesSneakingAndSprintingAsPlayerStates() {
		assertEquals(PlayerState.START_SNEAKING, state(PlayerCommandAction.PRESS_SHIFT_KEY));
		assertEquals(PlayerState.STOP_SNEAKING, state(PlayerCommandAction.RELEASE_SHIFT_KEY));
		assertEquals(PlayerState.START_SPRINTING, state(PlayerCommandAction.START_SPRINTING));
		assertEquals(PlayerState.STOP_SPRINTING, state(PlayerCommandAction.STOP_SPRINTING));
	}

	@Test
	void decodesTheAbilitiesOfThePlayer() {
		assertEquals(ClientboundPlayerAbilities.builder().invulnerable(true).flying(false).mayFly(true)
						.instabuild(true).flyingSpeed(0.05f).walkingSpeed(0.1f).build(),
				packets.decode(new ServerPlayerAbilitiesPacket(true, true, false, true, 0.05f, 0.1f)));
	}

	private PlayerState state(PlayerCommandAction action) {
		ClientPlayerStatePacket state = assertInstanceOf(ClientPlayerStatePacket.class,
				packets.encode(new ServerboundPlayerCommand(7, action)));
		assertEquals(7, state.getEntityId());
		return state.getState();
	}
}
