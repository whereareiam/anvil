package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.mcprotocol.client.EntityDataLayout;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entities of the MCProtocolLib releases from 1.20.5 on, which move, describe, mount and remove entities alike. Each
 * release's {@code EntityPackets} names the packets it carries and reads the packets that changed between releases:
 * how entities spawn and how the server places them.
 *
 * <p>Entity data is decoded by the entity's type through the release's {@link EntityDataLayout}, so each session
 * decodes through a translation of its own: it remembers the type of every entity the session added until the server
 * removes the entity, and forgets them all when the player joins a level, respawns or returns to configuration, which
 * the connection translation reports. Packets of one session arrive on one thread.</p>
 */
public abstract class EntityTranslation implements PacketTranslation<Packet> {
	private final Map<Integer, EntityType> types = new HashMap<>();
	private final EntityDataLayout<EntityType> layout;

	/**
	 * Creates the translation of a release whose entity data indexes the layout describes.
	 *
	 * @param layout the release's entity data layout
	 */
	protected EntityTranslation(@NotNull EntityDataLayout<EntityType> layout) {
		this.layout = layout;
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundLoginPacket ignored -> forget();
			case ClientboundRespawnPacket ignored -> forget();
			case ClientboundStartConfigurationPacket ignored -> forget();
			case ClientboundMoveEntityPosPacket move -> ClientboundMoveEntityPos.builder()
					.entityId(move.getEntityId())
					.delta(new Vec3d(move.getMoveX(), move.getMoveY(), move.getMoveZ()))
					.build();
			case ClientboundMoveEntityPosRotPacket move -> ClientboundMoveEntityPosRot.builder()
					.entityId(move.getEntityId())
					.delta(new Vec3d(move.getMoveX(), move.getMoveY(), move.getMoveZ()))
					.yaw(move.getYaw())
					.pitch(move.getPitch())
					.build();
			case ClientboundMoveEntityRotPacket turn ->
					new ClientboundMoveEntityRot(turn.getEntityId(), turn.getYaw(), turn.getPitch());
			case ClientboundSetEntityDataPacket data -> data(data.getEntityId(), data.getMetadata());
			case ClientboundSetPassengersPacket passengers -> new ClientboundSetPassengers(passengers.getEntityId(),
					Arrays.stream(passengers.getPassengerIds()).boxed().toList());
			case ClientboundRemoveEntitiesPacket removed -> removed(removed.getEntityIds());
			default -> decodeRelease(packet);
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads a packet whose form the release decides, such as an entity's spawn or a teleport.
	 *
	 * @param packet a packet the release received
	 * @return the protocol's packet, or {@code null} when the packet is not one of the release's entity packets
	 */
	protected abstract @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet);

	/**
	 * Remembers the type of an entity the server added, so that its entity data can be read, and reports the spawn.
	 *
	 * @param entityId the entity's protocol ID
	 * @param uniqueId the entity's unique ID, or {@code null} when the release sends none
	 * @param type the library's entity type
	 * @param position where the entity appears
	 * @param yaw the entity's horizontal direction in degrees
	 * @param pitch the entity's vertical direction in degrees
	 * @return the added entity
	 */
	protected final @NotNull ClientboundAddEntity added(
			int entityId,
			@Nullable UUID uniqueId,
			@NotNull EntityType type,
			@NotNull Vec3d position,
			float yaw,
			float pitch
	) {
		types.put(entityId, type);
		return ClientboundAddEntity.builder()
				.entityId(entityId)
				.uniqueId(uniqueId)
				.type(ValueTranslator.entityType(type))
				.position(position)
				.yaw(yaw)
				.pitch(pitch)
				.build();
	}

	/**
	 * Forgets every entity type of the level the player leaves; the connection translation reports the packet.
	 */
	private @Nullable ClientboundPacket forget() {
		types.clear();
		return null;
	}

	private ClientboundRemoveEntities removed(int[] entityIds) {
		for (int entityId : entityIds) types.remove(entityId);
		return new ClientboundRemoveEntities(Arrays.stream(entityIds).boxed().toList());
	}

	private ClientboundSetEntityData data(int entityId, EntityMetadata<?, ?>[] metadata) {
		EntityType type = types.get(entityId);
		List<EntityDataValue> values = new ArrayList<>();
		for (EntityMetadata<?, ?> entry : metadata) {
			EntityDataField field = layout.field(type, entry.getId());
			EntityDataValue value = field == null ? null : EntityDataValues.value(field, entry.getValue());
			if (value != null) values.add(value);
		}

		return new ClientboundSetEntityData(entityId, List.copyOf(values));
	}
}
