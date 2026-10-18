package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.mcprotocol.client.EntityDataLayout;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Entities of the MCProtocolLib releases from 1.18.2 until 1.19.4, which move, teleport, describe, mount and remove
 * entities alike; teleports place and turn an entity absolutely. Each release's {@code EntityPackets} reads how
 * entities spawn, which MCProtocolLib 1.19 reduced to the entity, player and experience orb packets, and names the
 * entity data layout of its release.
 *
 * <p>Entity data is decoded by the entity's type through the release's {@link EntityDataLayout}, so each session
 * decodes through a translation of its own: it remembers the type of every entity the session added until the server
 * removes the entity, and forgets them all when the player joins a level or respawns, which the connection translation
 * reports. Packets of one session arrive on one thread.</p>
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
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundAddEntity.class,
				ClientboundMoveEntityPos.class,
				ClientboundMoveEntityPosRot.class,
				ClientboundMoveEntityRot.class,
				ClientboundTeleportEntity.class,
				ClientboundSetEntityData.class,
				ClientboundSetPassengers.class,
				ClientboundRemoveEntities.class
		);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundLoginPacket ignored -> forget();
			case ClientboundRespawnPacket ignored -> forget();
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
			case ClientboundTeleportEntityPacket teleport -> ClientboundTeleportEntity.builder()
					.entityId(teleport.getEntityId())
					.position(new Vec3d(teleport.getX(), teleport.getY(), teleport.getZ()))
					.yaw(teleport.getYaw())
					.pitch(teleport.getPitch())
					.relatives(Set.of())
					.build();
			case ClientboundSetEntityDataPacket data -> data(data.getEntityId(), data.getMetadata());
			case ClientboundSetPassengersPacket passengers -> new ClientboundSetPassengers(passengers.getEntityId(),
					Arrays.stream(passengers.getPassengerIds()).boxed().toList());
			case ClientboundRemoveEntitiesPacket removed -> removed(removed.getEntityIds());
			default -> decodeSpawn(packet);
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads a packet that spawns an entity in the release's own form.
	 *
	 * @param packet a packet the release received
	 * @return the added entity, or {@code null} when the packet spawns no entity
	 */
	protected abstract @Nullable ClientboundPacket decodeSpawn(@NotNull Packet packet);

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
			EntityDataValue value = field == null ? null : value(field, entry.getValue());
			if (value != null) values.add(value);
		}

		return new ClientboundSetEntityData(entityId, List.copyOf(values));
	}

	/**
	 * Reads the value an index carries; a value of another kind than the table names, which only a server of another
	 * release sends, is left out. A release without displays names no index that carries a display's text.
	 */
	private static @Nullable EntityDataValue value(EntityDataField field, @Nullable Object value) {
		return switch (field) {
			case SHARED_FLAGS -> value instanceof Byte flags ? SharedFlags.of(flags) : null;
			case CUSTOM_NAME -> value instanceof Optional<?> name ? customName(name) : null;
			case CUSTOM_NAME_VISIBLE -> value instanceof Boolean visible ? new CustomNameVisible(visible) : null;
			case SHOWN_ITEM -> value == null || value instanceof ItemStack
					? new ShownItem(ValueTranslator.item((ItemStack) value))
					: null;
			case DISPLAY_TEXT -> value instanceof Component text ? new DisplayText(ValueTranslator.plain(text)) : null;
		};
	}

	private static @Nullable CustomName customName(Optional<?> name) {
		if (name.isEmpty()) return new CustomName(null);
		if (!(name.get() instanceof Component text)) return null;

		return new CustomName(ValueTranslator.plain(text));
	}
}
