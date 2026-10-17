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
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddExperienceOrb;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddMob;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddPainting;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddPlayer;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.mcprotocol.client.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Entities of Minecraft 1.18.2, which adds mobs, players, paintings and experience orbs through packets of their own,
 * hangs a painting at its block without a direction and teleports an entity absolutely.
 *
 * <p>Entity data is decoded by the entity's type through {@link EntityDataTable}, so each session decodes through a
 * translation of its own: it remembers the type of every entity the session added until the server removes the
 * entity, and forgets them all when the player joins a level or respawns, which the connection translation reports.
 * Packets of one session arrive on one thread.</p>
 */
final class EntityPackets implements PacketTranslation<Packet> {
	private final Map<Integer, EntityType> types = new HashMap<>();

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundAddEntity.class,
				ClientboundAddMob.class,
				ClientboundAddPlayer.class,
				ClientboundAddPainting.class,
				ClientboundAddExperienceOrb.class,
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
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundLoginPacket ignored -> forget();
			case ClientboundRespawnPacket ignored -> forget();
			case ClientboundAddEntityPacket add -> added(add);
			case ClientboundAddMobPacket add -> mobAdded(add);
			case ClientboundAddPlayerPacket add -> playerAdded(add);
			case ClientboundAddPaintingPacket add -> paintingAdded(add);
			case ClientboundAddExperienceOrbPacket orb -> orbAdded(orb);
			case ClientboundMoveEntityPosPacket move -> ClientboundMoveEntityPos.builder()
					.entityId(move.getEntityId())
					.deltaX(move.getMoveX())
					.deltaY(move.getMoveY())
					.deltaZ(move.getMoveZ())
					.build();
			case ClientboundMoveEntityPosRotPacket move -> ClientboundMoveEntityPosRot.builder()
					.entityId(move.getEntityId())
					.deltaX(move.getMoveX())
					.deltaY(move.getMoveY())
					.deltaZ(move.getMoveZ())
					.yaw(move.getYaw())
					.pitch(move.getPitch())
					.build();
			case ClientboundMoveEntityRotPacket turn ->
					new ClientboundMoveEntityRot(turn.getEntityId(), turn.getYaw(), turn.getPitch());
			case ClientboundTeleportEntityPacket teleport -> ClientboundTeleportEntity.builder()
					.entityId(teleport.getEntityId())
					.position(new EntityPosition(teleport.getX(), teleport.getY(), teleport.getZ()))
					.yaw(teleport.getYaw())
					.pitch(teleport.getPitch())
					.relatives(Set.of())
					.build();
			case ClientboundSetEntityDataPacket data -> data(data.getEntityId(), data.getMetadata());
			case ClientboundSetPassengersPacket passengers -> new ClientboundSetPassengers(passengers.getEntityId(),
					Arrays.stream(passengers.getPassengerIds()).boxed().toList());
			case ClientboundRemoveEntitiesPacket removed -> removed(removed.getEntityIds());
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Forgets every entity type of the level the player leaves; the connection translation reports the packet.
	 */
	private @Nullable ClientboundPacket forget() {
		types.clear();
		return null;
	}

	private ClientboundAddEntity added(ClientboundAddEntityPacket add) {
		types.put(add.getEntityId(), add.getType());
		return ClientboundAddEntity.builder()
				.entityId(add.getEntityId())
				.uniqueId(add.getUuid())
				.type(key(add.getType()))
				.position(new EntityPosition(add.getX(), add.getY(), add.getZ()))
				.yaw(add.getYaw())
				.pitch(add.getPitch())
				.build();
	}

	private ClientboundAddMob mobAdded(ClientboundAddMobPacket add) {
		types.put(add.getEntityId(), add.getType());
		return ClientboundAddMob.builder()
				.entityId(add.getEntityId())
				.uniqueId(add.getUuid())
				.type(key(add.getType()))
				.position(new EntityPosition(add.getX(), add.getY(), add.getZ()))
				.yaw(add.getYaw())
				.pitch(add.getPitch())
				.build();
	}

	private ClientboundAddPlayer playerAdded(ClientboundAddPlayerPacket add) {
		types.put(add.getEntityId(), EntityType.PLAYER);
		return ClientboundAddPlayer.builder()
				.entityId(add.getEntityId())
				.uniqueId(add.getUuid())
				.position(new EntityPosition(add.getX(), add.getY(), add.getZ()))
				.yaw(add.getYaw())
				.pitch(add.getPitch())
				.build();
	}

	private ClientboundAddPainting paintingAdded(ClientboundAddPaintingPacket add) {
		types.put(add.getEntityId(), EntityType.PAINTING);
		return ClientboundAddPainting.builder()
				.entityId(add.getEntityId())
				.uniqueId(add.getUuid())
				.position(BlockPosition.builder()
						.x(add.getPosition().getX())
						.y(add.getPosition().getY())
						.z(add.getPosition().getZ())
						.build())
				.build();
	}

	private ClientboundAddExperienceOrb orbAdded(ClientboundAddExperienceOrbPacket orb) {
		types.put(orb.getEntityId(), EntityType.EXPERIENCE_ORB);
		return ClientboundAddExperienceOrb.builder()
				.entityId(orb.getEntityId())
				.position(new EntityPosition(orb.getX(), orb.getY(), orb.getZ()))
				.build();
	}

	private ClientboundRemoveEntities removed(int[] entityIds) {
		for (int entityId : entityIds) types.remove(entityId);
		return new ClientboundRemoveEntities(Arrays.stream(entityIds).boxed().toList());
	}

	private ClientboundSetEntityData data(int entityId, EntityMetadata<?, ?>[] metadata) {
		EntityType type = types.get(entityId);
		List<EntityDataValue> values = new ArrayList<>();
		for (EntityMetadata<?, ?> entry : metadata) {
			EntityDataTable.Field field = EntityDataTable.field(type, entry.getId());
			EntityDataValue value = field == null ? null : value(field, entry.getValue());
			if (value != null) values.add(value);
		}
		return new ClientboundSetEntityData(entityId, List.copyOf(values));
	}

	/**
	 * Reads the value an index carries; a value of another kind than the table names, which only a server of another
	 * release sends, is left out.
	 */
	private static @Nullable EntityDataValue value(EntityDataTable.Field field, @Nullable Object value) {
		return switch (field) {
			case SHARED_FLAGS -> value instanceof Byte flags ? EntityDataTable.sharedFlags(flags) : null;
			case CUSTOM_NAME -> value instanceof Optional<?> name ? customName(name) : null;
			case CUSTOM_NAME_VISIBLE -> value instanceof Boolean visible ? new CustomNameVisible(visible) : null;
			case SHOWN_ITEM -> value == null || value instanceof ItemStack ? new ShownItem(item((ItemStack) value)) : null;
		};
	}

	private static @Nullable CustomName customName(Optional<?> name) {
		if (name.isEmpty()) return new CustomName(null);
		if (!(name.get() instanceof Component text)) return null;

		return new CustomName(AdventureText.plain(text));
	}

	private static me.whereareiam.anvil.protocol.java.api.model.inventory.@Nullable ItemStack item(
			@Nullable ItemStack stack
	) {
		if (stack == null || stack.getAmount() <= 0) return null;

		return new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(stack.getId(), stack.getAmount());
	}

	private static EntityTypeKey key(EntityType type) {
		return EntityTypeKey.minecraft(type.name().toLowerCase(Locale.ROOT));
	}
}
