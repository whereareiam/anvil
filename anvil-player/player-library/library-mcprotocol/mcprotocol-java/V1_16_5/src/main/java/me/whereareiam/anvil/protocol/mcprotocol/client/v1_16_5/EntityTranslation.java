package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.Animation;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityAnimationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityMetadataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntitySetPassengersPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityTeleportPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityVelocityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnExpOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnLivingEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPlayerPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
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
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
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
import java.util.Set;
import java.util.UUID;

/**
 * Entities of the MCProtocolLib releases from 1.16.5 until 1.17.1, which spawn objects, mobs, players, paintings and
 * experience orbs through packets of their own; the translation reports each of them as an added entity, a painting at
 * the corner of the block it hangs at and facing no direction, as it does an orb, which has no unique ID either.
 * Teleports place and turn an entity absolutely. Each release's {@code EntityPackets} reads the removal, whose packet
 * Minecraft 1.17 renamed, and names the entity data layout of its release.
 *
 * <p>Entity data is decoded by the entity's type through the release's {@link EntityDataLayout}, so each session
 * decodes through a translation of its own: it remembers the type of every entity the session added until the server
 * removes the entity, and forgets them all when the player joins a level or respawns, which the connection translation
 * reports. Packets of one session arrive on one thread.</p>
 */
public abstract class EntityTranslation implements PacketTranslation<Packet> {
	private static final float NO_DIRECTION = 0;

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
				ClientboundSetEntityMotion.class,
				ClientboundSetPassengers.class,
				ClientboundSwingAnimation.class,
				ClientboundRemoveEntities.class
		);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ServerJoinGamePacket ignored -> forget();
			case ServerRespawnPacket ignored -> forget();
			case ServerSpawnEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ServerSpawnLivingEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ServerSpawnPlayerPacket add -> added(add.getEntityId(), add.getUuid(), EntityType.PLAYER,
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ServerSpawnPaintingPacket add -> added(add.getEntityId(), add.getUuid(), EntityType.PAINTING,
					corner(add.getPosition()), NO_DIRECTION, NO_DIRECTION);
			case ServerSpawnExpOrbPacket orb -> added(orb.getEntityId(), null, EntityType.EXPERIENCE_ORB,
					new Vec3d(orb.getX(), orb.getY(), orb.getZ()), NO_DIRECTION, NO_DIRECTION);
			case ServerEntityPositionPacket move -> ClientboundMoveEntityPos.builder()
					.entityId(move.getEntityId())
					.delta(new Vec3d(move.getMoveX(), move.getMoveY(), move.getMoveZ()))
					.build();
			case ServerEntityPositionRotationPacket move -> ClientboundMoveEntityPosRot.builder()
					.entityId(move.getEntityId())
					.delta(new Vec3d(move.getMoveX(), move.getMoveY(), move.getMoveZ()))
					.yaw(move.getYaw())
					.pitch(move.getPitch())
					.build();
			case ServerEntityRotationPacket turn ->
					new ClientboundMoveEntityRot(turn.getEntityId(), turn.getYaw(), turn.getPitch());
			case ServerEntityTeleportPacket teleport -> ClientboundTeleportEntity.builder()
					.entityId(teleport.getEntityId())
					.position(new Vec3d(teleport.getX(), teleport.getY(), teleport.getZ()))
					.yaw(teleport.getYaw())
					.pitch(teleport.getPitch())
					.relatives(Set.of())
					.build();
			case ServerEntityMetadataPacket data -> data(data.getEntityId(), data.getMetadata());
			case ServerEntitySetPassengersPacket passengers -> new ClientboundSetPassengers(passengers.getEntityId(),
					Arrays.stream(passengers.getPassengerIds()).boxed().toList());
			case ServerEntityVelocityPacket motion -> new ClientboundSetEntityMotion(motion.getEntityId(),
					new Vec3d(motion.getMotionX(), motion.getMotionY(), motion.getMotionZ()));
			case ServerEntityAnimationPacket animation -> swing(animation.getEntityId(), animation.getAnimation());
			default -> decodeRemoval(packet);
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads the release's packet that removes entities.
	 *
	 * @param packet a packet the release received
	 * @return the removed entities, or {@code null} when the packet removes none
	 */
	protected abstract @Nullable ClientboundPacket decodeRemoval(@NotNull Packet packet);

	/**
	 * Forgets the types of removed entities and reports their removal.
	 *
	 * @param entityIds the protocol IDs of the removed entities
	 * @return the removed entities
	 */
	protected final @NotNull ClientboundRemoveEntities removed(int @NotNull [] entityIds) {
		for (int entityId : entityIds) types.remove(entityId);
		return new ClientboundRemoveEntities(Arrays.stream(entityIds).boxed().toList());
	}

	/**
	 * Reads an animation that swings an arm. The library names the off hand's swing, animation 3, after the eating it
	 * stood for before Minecraft 1.9.
	 */
	private static @Nullable ClientboundPacket swing(int entityId, Animation animation) {
		if (animation == Animation.SWING_ARM) return new ClientboundSwingAnimation(entityId, InteractionHand.MAIN_HAND);
		if (animation == Animation.EAT_FOOD) return new ClientboundSwingAnimation(entityId, InteractionHand.OFF_HAND);

		return null;
	}

	/**
	 * Forgets every entity type of the level the player leaves; the connection translation reports the packet.
	 */
	private @Nullable ClientboundPacket forget() {
		types.clear();
		return null;
	}

	private ClientboundAddEntity added(
			int entityId,
			@Nullable UUID uniqueId,
			EntityType type,
			Vec3d position,
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

	private ClientboundSetEntityData data(int entityId, EntityMetadata[] metadata) {
		EntityType type = types.get(entityId);
		List<EntityDataValue> values = new ArrayList<>();
		for (EntityMetadata entry : metadata) {
			EntityDataField field = layout.field(type, entry.getId());
			EntityDataValue value = field == null ? null : value(field, entry.getValue());
			if (value != null) values.add(value);
		}

		return new ClientboundSetEntityData(entityId, List.copyOf(values));
	}

	/**
	 * Places a painting at the corner of the block it hangs at.
	 */
	private static Vec3d corner(Position block) {
		return new Vec3d(block.getX(), block.getY(), block.getZ());
	}

	/**
	 * Reads the value an index carries; a value of another kind than the table names, which only a server of another
	 * release sends, is left out. These releases decode an absent custom name as {@code null} rather than as an empty
	 * optional, and have no displays, so no index carries a display's text.
	 */
	private static @Nullable EntityDataValue value(EntityDataField field, @Nullable Object value) {
		return switch (field) {
			case SHARED_FLAGS -> value instanceof Byte flags ? SharedFlags.of(flags) : null;
			case CUSTOM_NAME -> customName(value);
			case CUSTOM_NAME_VISIBLE -> value instanceof Boolean visible ? new CustomNameVisible(visible) : null;
			case SHOWN_ITEM -> value == null || value instanceof ItemStack
					? new ShownItem(ValueTranslator.item((ItemStack) value))
					: null;
			case DISPLAY_TEXT -> null;
		};
	}

	private static @Nullable CustomName customName(@Nullable Object name) {
		if (name == null) return new CustomName(null);
		if (!(name instanceof Component text)) return null;

		return new CustomName(ValueTranslator.plain(text));
	}
}
