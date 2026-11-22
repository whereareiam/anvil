package me.whereareiam.anvil.edition.java.protocol.worker.answer;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundPing;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ServerboundPong;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ClientboundAnimate;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerAck;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerClose;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundOpenScreen;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerAck;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClientAnswersTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final DimensionType OVERWORLD_TYPE = DimensionType.known(OVERWORLD);
	private static final short UID = -3;
	private static final EntityId OWN = new EntityId(7);

	@Test
	void answersEachPingWithItsIdBeforeTheAcceptanceOfALaterTeleportAsThePacketsArrived() {
		TestJavaPlayer player = joined();

		player.deliver(new ClientboundPing(-1));
		player.deliver(ClientboundPlayerPosition.builder().id(2).position(new Vec3d(0.5, 64.0, 0.5)).relatives(Set.of()).build());
		player.deliver(new ClientboundPing(-2));

		List<?> sent = player.sent();
		assertEquals(4, sent.size(), sent::toString);
		assertEquals(new ServerboundPong(-1), sent.getFirst());
		assertInstanceOf(ServerboundAcceptTeleportation.class, sent.get(1));
		assertInstanceOf(ServerboundMovePlayerPosRot.class, sent.get(2));
		assertEquals(new ServerboundPong(-2), sent.getLast());
	}

	@Test
	void answersAPingBeforeThePlayerJoinedALevelAsInTheConfiguration() {
		TestJavaPlayer player = new TestJavaPlayer("Alice");
		player.connect();

		player.deliver(new ClientboundPing(42));

		assertEquals(List.of(new ServerboundPong(42)), player.sent());
	}

	@Test
	void acknowledgesARejectionInTheInventoryOrTheOpenContainerAndIgnoresOthers() {
		TestJavaPlayer player = joined();

		player.deliver(new ClientboundContainerAck(0, UID, false));
		player.deliver(new ClientboundContainerAck(0, UID, true));
		player.deliver(new ClientboundContainerAck(5, UID, false));
		player.deliver(new ClientboundOpenScreen(5, "minecraft:generic_9x3", Text.literal("Chest")));
		player.deliver(new ClientboundContainerAck(5, UID, false));
		player.deliver(new ClientboundContainerAck(6, UID, false));

		assertEquals(List.of(new ServerboundContainerAck(0, UID, true), new ServerboundContainerAck(5, UID, true)),
				player.sent());
	}

	@Test
	void showsTheInventoryAgainOnceTheServerClosesTheContainerOrThePlayerRespawns() {
		TestJavaPlayer player = joined();

		player.deliver(new ClientboundOpenScreen(5, "minecraft:generic_9x3", Text.literal("Chest")));
		player.deliver(new ClientboundContainerClose(9));
		player.deliver(new ClientboundContainerAck(5, UID, false));
		player.deliver(new ClientboundOpenScreen(6, "minecraft:generic_9x3", Text.literal("Chest")));
		player.deliver(ClientboundRespawn.builder().dimension(OVERWORLD).dimensionType(OVERWORLD_TYPE).build());
		player.deliver(new ClientboundContainerAck(6, UID, false));

		assertEquals(List.of(), player.sent());
	}

	@Test
	void swingsBackEachArmTheServerSwingsOfThePlayersOwnEntityBefore26_3InTheOrderThePacketsArrived() {
		TestJavaPlayer player = joined();

		player.deliver(new ClientboundAnimate(OWN, AnimateAction.SWING_MAIN_HAND));
		player.deliver(new ClientboundPing(-1));
		player.deliver(new ClientboundAnimate(OWN, AnimateAction.SWING_OFF_HAND));
		player.deliver(new ClientboundAnimate(OWN, AnimateAction.CRITICAL_HIT));
		player.deliver(new ClientboundAnimate(new EntityId(8), AnimateAction.SWING_MAIN_HAND));

		assertEquals(List.of(new ServerboundSwing(InteractionHand.MAIN_HAND), new ServerboundPong(-1),
				new ServerboundSwing(InteractionHand.OFF_HAND)), player.sent());
	}

	@Test
	void swingsBackNothingBeforeThePlayerJoinedALevelOrFrom26_3() {
		TestJavaPlayer connected = new TestJavaPlayer("Alice");
		connected.connect();
		connected.deliver(new ClientboundAnimate(OWN, AnimateAction.SWING_MAIN_HAND));
		TestJavaPlayer current = joined(MinecraftVersion.parse("26.3"), 777);
		current.deliver(new ClientboundAnimate(OWN, AnimateAction.SWING_MAIN_HAND));

		assertEquals(List.of(), connected.sent());
		assertEquals(List.of(), current.sent());
	}

	private static @NotNull TestJavaPlayer joined() {
		return joined(TestJavaPlayer.CLIENT_VERSION, TestJavaPlayer.PROTOCOL);
	}

	private static @NotNull TestJavaPlayer joined(@NotNull MinecraftVersion version, int protocol) {
		TestJavaPlayer player = new TestJavaPlayer("Alice", version, protocol, type -> true);
		player.connect();
		player.deliver(ClientboundLogin.builder().entityId(OWN).level(OVERWORLD).dimension(OVERWORLD)
				.dimensionType(OVERWORLD_TYPE).build());
		return player;
	}
}
