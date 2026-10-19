package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.SwingAnimation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSwingAnimationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityPacketsTest {
	private static final SwingAnimation WHACK = new SwingAnimation(SwingAnimation.Type.WHACK, 6);

	private final EntityPackets packets = new EntityPackets();

	@Test
	void readsTheSwingOfAnArmFromItsOwnPacket() {
		assertEquals(new ClientboundSwingAnimation(7, InteractionHand.MAIN_HAND),
				packets.decode(new ClientboundSwingAnimationPacket(7, Hand.MAIN_HAND, WHACK)));
		assertEquals(new ClientboundSwingAnimation(8, InteractionHand.OFF_HAND),
				packets.decode(new ClientboundSwingAnimationPacket(8, Hand.OFF_HAND, WHACK)));
	}

	@Test
	void decodesASpawnAndTheDataOfADisplayWithThe1_21_11Layout() {
		assertEquals(ClientboundAddEntity.builder().entityId(8).uniqueId(new UUID(0, 8))
						.type(EntityTypeKey.minecraft("text_display")).position(new Vec3d(0, 66, 0)).build(),
				packets.decode(new ClientboundAddEntityPacket(8, new UUID(0, 8), EntityType.TEXT_DISPLAY, 0, 66, 0, 0,
						0, 0)));

		assertEquals(new ClientboundSetEntityData(8, List.of(new DisplayText("bubble"))),
				packets.decode(new ClientboundSetEntityDataPacket(8, new ObjectEntityMetadata<?>[]{
						new ObjectEntityMetadata<>(23, MetadataTypes.COMPONENT, Component.text("bubble"))
				})));
	}

	@Test
	void decodesRelativeTeleports() {
		assertEquals(ClientboundTeleportEntity.builder().entityId(7).position(new Vec3d(1, 2, 3)).yaw(15).pitch(5)
						.relatives(Set.of(Relative.X, Relative.PITCH)).build(),
				packets.decode(new ClientboundTeleportEntityPacket(7, Vector3d.from(1, 2, 3), Vector3d.ZERO, 15, 5,
						List.of(PositionElement.X, PositionElement.X_ROT), false)));
	}

	@Test
	void placesAnEntitySyncedAtOnceWhereTheSyncEnds() {
		ClientboundEntityPositionSyncPacket sync = new ClientboundEntityPositionSyncPacket(7, false, Vector3d.from(4, 5, 6),
				null, 30, 20, true);

		assertEquals(ClientboundEntityPositionSync.builder().entityId(7).position(new Vec3d(4, 5, 6)).yaw(30).pitch(20)
				.build(), packets.decode(sync));
	}

	@Test
	void placesAnEntitySyncedInStepsWhereTheLastStepEnds() {
		ClientboundEntityPositionSyncPacket sync = new ClientboundEntityPositionSyncPacket(7, true, null, List.of(
				new ClientboundEntityPositionSyncPacket.PositionStep(Vector3d.from(2, 5, 6), 1),
				new ClientboundEntityPositionSyncPacket.PositionStep(Vector3d.from(4, 5, 6), 2)
		), 30, 20, true);

		assertEquals(ClientboundEntityPositionSync.builder().entityId(7).position(new Vec3d(4, 5, 6)).yaw(30).pitch(20)
				.build(), packets.decode(sync));
	}
}
