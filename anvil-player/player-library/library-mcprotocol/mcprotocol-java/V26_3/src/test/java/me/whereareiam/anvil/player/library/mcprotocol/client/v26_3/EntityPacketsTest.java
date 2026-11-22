package me.whereareiam.anvil.player.library.mcprotocol.client.v26_3;

import me.whereareiam.anvil.edition.java.protocol.model.entity.DisplayText;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityTypeKey;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.ComponentItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11.TextTranslator;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityPacketsTest {
	private final EntityPackets packets = new EntityPackets(new TextTranslator(), new ComponentItemTranslator(new TextTranslator()));

	@Test
	void decodesASpawnAndTheDataOfADisplayWithThe1_21_11Layout() {
		assertEquals(ClientboundAddEntity.builder().entityId(new EntityId(8)).uniqueId(new UUID(0, 8))
						.type(EntityTypeKey.minecraft("text_display")).position(new Vec3d(0, 66, 0)).build(),
				packets.decode(new ClientboundAddEntityPacket(8, new UUID(0, 8), EntityType.TEXT_DISPLAY, 0, 66, 0, 0,
						0, 0)));

		assertEquals(new ClientboundSetEntityData(new EntityId(8), List.of(new DisplayText("bubble"))),
				packets.decode(new ClientboundSetEntityDataPacket(8, new ObjectEntityMetadata<?>[]{
						new ObjectEntityMetadata<>(23, MetadataTypes.COMPONENT, Component.text("bubble"))
				})));
	}

	@Test
	void decodesRelativeTeleports() {
		assertEquals(ClientboundTeleportEntity.builder().entityId(new EntityId(7)).position(new Vec3d(1, 2, 3)).yaw(15).pitch(5)
						.relatives(Set.of(Relative.X, Relative.PITCH)).build(),
				packets.decode(new ClientboundTeleportEntityPacket(7, Vector3d.from(1, 2, 3), Vector3d.ZERO, 15, 5,
						List.of(PositionElement.X, PositionElement.X_ROT), false)));
	}

	@Test
	void placesAnEntitySyncedAtOnceWhereTheSyncEnds() {
		ClientboundEntityPositionSyncPacket sync = new ClientboundEntityPositionSyncPacket(7, false, Vector3d.from(4, 5, 6),
				null, 30, 20, true);

		assertEquals(ClientboundEntityPositionSync.builder().entityId(new EntityId(7)).position(new Vec3d(4, 5, 6)).yaw(30).pitch(20)
				.build(), packets.decode(sync));
	}

	@Test
	void placesAnEntitySyncedInStepsWhereTheLastStepEnds() {
		ClientboundEntityPositionSyncPacket sync = new ClientboundEntityPositionSyncPacket(7, true, null, List.of(
				new ClientboundEntityPositionSyncPacket.PositionStep(Vector3d.from(2, 5, 6), 1),
				new ClientboundEntityPositionSyncPacket.PositionStep(Vector3d.from(4, 5, 6), 2)
		), 30, 20, true);

		assertEquals(ClientboundEntityPositionSync.builder().entityId(new EntityId(7)).position(new Vec3d(4, 5, 6)).yaw(30).pitch(20)
				.build(), packets.decode(sync));
	}

	@Test
	void decodesTheVelocityTheServerSets() {
		assertEquals(new ClientboundSetEntityMotion(new EntityId(7), new Vec3d(0, 0.42, 0)),
				packets.decode(new ClientboundSetEntityMotionPacket(7, Vector3d.from(0, 0.42, 0))));
	}
}
