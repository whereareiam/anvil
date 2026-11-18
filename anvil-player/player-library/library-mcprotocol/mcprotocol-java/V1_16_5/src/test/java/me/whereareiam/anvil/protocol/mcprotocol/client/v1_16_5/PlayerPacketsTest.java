package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerStatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.edition.java.protocol.type.player.PlayerCommandAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void encodesThePositionAndRotationWithoutTheHorizontalCollision() {
		ClientPlayerPositionRotationPacket packet = assertInstanceOf(ClientPlayerPositionRotationPacket.class,
				packets.encode(ServerboundMovePlayerPosRot.builder().position(new Vec3d(1.5, 64, -2.5)).yaw(90)
						.pitch(-15).onGround(true).horizontalCollision(true).build()));

		assertTrue(packet.isOnGround());
		assertEquals(List.of(1.5, 64.0, -2.5), List.of(packet.getX(), packet.getY(), packet.getZ()));
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void encodesThePositionTheRotationAndTheGroundStateAlone() {
		ClientPlayerPositionPacket position = assertInstanceOf(ClientPlayerPositionPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, 2, 3)).onGround(true)
						.horizontalCollision(true).build()));
		ClientPlayerRotationPacket rotation = assertInstanceOf(ClientPlayerRotationPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).build()));
		ClientPlayerMovementPacket status = assertInstanceOf(ClientPlayerMovementPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).build()));

		assertEquals(List.of(1.0, 2.0, 3.0), List.of(position.getX(), position.getY(), position.getZ()));
		assertTrue(position.isOnGround());
		assertEquals(45, rotation.getYaw());
		assertEquals(10, rotation.getPitch());
		assertFalse(rotation.isOnGround());
		assertTrue(status.isOnGround());
	}

	@Test
	void encodesEveryPlayerCommandAsThePlayerStateOfTheEntity() {
		Map<PlayerCommandAction, PlayerState> states = Map.of(
				PlayerCommandAction.PRESS_SHIFT_KEY, PlayerState.START_SNEAKING,
				PlayerCommandAction.RELEASE_SHIFT_KEY, PlayerState.STOP_SNEAKING,
				PlayerCommandAction.START_SPRINTING, PlayerState.START_SPRINTING,
				PlayerCommandAction.STOP_SPRINTING, PlayerState.STOP_SPRINTING
		);

		for (PlayerCommandAction action : PlayerCommandAction.values()) {
			ClientPlayerStatePacket state = assertInstanceOf(ClientPlayerStatePacket.class,
					packets.encode(new ServerboundPlayerCommand(new EntityId(7), action)));
			assertEquals(7, state.getEntityId());
			assertEquals(states.get(action), state.getState());
		}
	}

	@Test
	void decodesThePlacementKeepingTheVelocityAlongEachRelativeCoordinate() {
		ServerPlayerPositionRotationPacket position = new ServerPlayerPositionRotationPacket(10, 64, -10, 90, 5, 1,
				List.of(PositionElement.X, PositionElement.Z, PositionElement.YAW));

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
						.deltaMovement(Vec3d.ZERO)
						.relatives(Set.of(Relative.X, Relative.DELTA_X, Relative.Z, Relative.DELTA_Z, Relative.YAW))
						.build(),
				packets.decode(position));
	}

	@Test
	void decodesAnAbsolutePlacementAsStoppingTheVelocity() {
		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
						.relatives(Set.of()).build(),
				packets.decode(new ServerPlayerPositionRotationPacket(10, 64, -10, 90, 5, 1, List.of())));
	}

	@Test
	void decodesTheAbilitiesOfThePlayer() {
		assertEquals(ClientboundPlayerAbilities.builder().invulnerable(true).flying(true).mayFly(true)
						.instabuild(false).flyingSpeed(0.05f).walkingSpeed(0.1f).build(),
				packets.decode(new ServerPlayerAbilitiesPacket(true, true, true, false, 0.05f, 0.1f)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesNeitherThePlayerInputNorTheEndOfAClientTick() {
		assertEquals(Set.of(ClientboundPlayerAbilities.class, ClientboundPlayerPosition.class,
				ServerboundMovePlayerPos.class, ServerboundMovePlayerPosRot.class, ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class, ServerboundPlayerAbilities.class,
				ServerboundPlayerCommand.class), packets.packets());
		assertFalse(packets.packets().contains(ServerboundPlayerInput.class));
		assertFalse(packets.packets().contains(ServerboundClientTickEnd.class));
	}

	@Test
	void encodesTheFlightThePlayerStartsOrStopsAsTheOnlyAbilityItTells() {
		ClientPlayerAbilitiesPacket flying = assertInstanceOf(ClientPlayerAbilitiesPacket.class,
				packets.encode(new ServerboundPlayerAbilities(true)));
		ClientPlayerAbilitiesPacket landed = assertInstanceOf(ClientPlayerAbilitiesPacket.class,
				packets.encode(new ServerboundPlayerAbilities(false)));

		assertTrue(flying.isFlying());
		assertFalse(landed.isFlying());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
