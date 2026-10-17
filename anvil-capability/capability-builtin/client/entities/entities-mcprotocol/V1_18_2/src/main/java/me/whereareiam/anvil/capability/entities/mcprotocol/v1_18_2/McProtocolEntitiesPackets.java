package me.whereareiam.anvil.capability.entities.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import me.whereareiam.anvil.capability.entities.packet.EntitiesPackets;
import me.whereareiam.anvil.capability.entities.packet.model.EntitiesRemoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityDataChanged;
import me.whereareiam.anvil.capability.entities.packet.model.EntityMoved;
import me.whereareiam.anvil.capability.entities.packet.model.EntityPacket;
import me.whereareiam.anvil.capability.entities.packet.model.EntitySpawned;
import me.whereareiam.anvil.capability.entities.packet.model.EntityTurned;
import me.whereareiam.anvil.capability.entities.packet.model.PassengersChanged;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerJoined;
import me.whereareiam.anvil.capability.entities.packet.model.PlayerMoved;
import me.whereareiam.anvil.capability.entities.packet.model.WorldCleared;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomName;
import me.whereareiam.anvil.capability.entities.packet.model.data.CustomNameVisible;
import me.whereareiam.anvil.capability.entities.packet.model.data.EntityData;
import me.whereareiam.anvil.capability.entities.packet.model.data.SharedFlags;
import me.whereareiam.anvil.capability.entities.packet.model.data.ShownItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Entity packets of the MCProtocolLib release for Minecraft 1.18.2, which spawns mobs, players, paintings and
 * experience orbs through packets of their own, places a painting at its block without rotation, and has no
 * configuration phase. Teleports place and turn an entity absolutely, and only the player's position names relative
 * coordinates. Listeners follow packetlib 2.1 and components are adventure 4.9.
 *
 * <p>Entity data of every entity keeps its shared flags at index 0, the optional custom name at 2 and whether it is
 * shown at 3. Dropped items, item frames, thrown items and fireworks keep the item they show at 8, where no other
 * entity has an item. The release has no displays, which arrived in Minecraft 1.19.4.</p>
 */
public final class McProtocolEntitiesPackets implements EntitiesPackets<Session> {
	private static final int SHARED_FLAGS = 0;
	private static final int CUSTOM_NAME = 2;
	private static final int CUSTOM_NAME_VISIBLE = 3;
	private static final int SHOWN_ITEM = 8;

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull Consumer<EntityPacket> packets) {
		SessionListener listener = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				received(packet).forEach(packets);
			}

			@Override
			public void packetSent(Session sent, Packet packet) {
				EntityPacket decoded = sent(packet);
				if (decoded != null) packets.accept(decoded);
			}

			@Override
			public void disconnected(DisconnectedEvent event) {
				packets.accept(new WorldCleared());
			}
		};
		session.addListener(listener);
		return () -> session.removeListener(listener);
	}

	private static List<EntityPacket> received(Packet packet) {
		return switch (packet) {
			case ClientboundLoginPacket login -> List.of(new PlayerJoined(login.getEntityId()));
			case ClientboundRespawnPacket ignored -> List.of(new WorldCleared());
			case ClientboundPlayerPositionPacket position -> List.of(PlayerMoved.builder()
					.x(position.getX())
					.y(position.getY())
					.z(position.getZ())
					.relativeX(position.getRelative().contains(PositionElement.X))
					.relativeY(position.getRelative().contains(PositionElement.Y))
					.relativeZ(position.getRelative().contains(PositionElement.Z))
					.build());
			case ClientboundAddEntityPacket spawn -> List.of(EntitySpawned.builder()
					.entityId(spawn.getEntityId())
					.uniqueId(spawn.getUuid())
					.type("minecraft:" + spawn.getType().name().toLowerCase(Locale.ROOT))
					.x(spawn.getX())
					.y(spawn.getY())
					.z(spawn.getZ())
					.yaw(spawn.getYaw())
					.pitch(spawn.getPitch())
					.build());
			case ClientboundAddMobPacket spawn -> List.of(EntitySpawned.builder()
					.entityId(spawn.getEntityId())
					.uniqueId(spawn.getUuid())
					.type("minecraft:" + spawn.getType().name().toLowerCase(Locale.ROOT))
					.x(spawn.getX())
					.y(spawn.getY())
					.z(spawn.getZ())
					.yaw(spawn.getYaw())
					.pitch(spawn.getPitch())
					.build());
			case ClientboundAddPlayerPacket spawn -> List.of(EntitySpawned.builder()
					.entityId(spawn.getEntityId())
					.uniqueId(spawn.getUuid())
					.type("minecraft:player")
					.x(spawn.getX())
					.y(spawn.getY())
					.z(spawn.getZ())
					.yaw(spawn.getYaw())
					.pitch(spawn.getPitch())
					.build());
			case ClientboundAddPaintingPacket spawn -> List.of(EntitySpawned.builder()
					.entityId(spawn.getEntityId())
					.uniqueId(spawn.getUuid())
					.type("minecraft:painting")
					.x(spawn.getPosition().getX())
					.y(spawn.getPosition().getY())
					.z(spawn.getPosition().getZ())
					.build());
			case ClientboundAddExperienceOrbPacket orb -> List.of(EntitySpawned.builder()
					.entityId(orb.getEntityId())
					.type("minecraft:experience_orb")
					.x(orb.getX())
					.y(orb.getY())
					.z(orb.getZ())
					.build());
			case ClientboundMoveEntityPosPacket move -> List.of(movedBy(move.getEntityId(), move.getMoveX(), move.getMoveY(), move.getMoveZ()));
			case ClientboundMoveEntityPosRotPacket move -> List.of(movedBy(move.getEntityId(), move.getMoveX(), move.getMoveY(), move.getMoveZ()),
					turnedTo(move.getEntityId(), move.getYaw(), move.getPitch()));
			case ClientboundMoveEntityRotPacket turn -> List.of(turnedTo(turn.getEntityId(), turn.getYaw(), turn.getPitch()));
			case ClientboundTeleportEntityPacket teleport -> List.of(EntityMoved.builder()
					.entityId(teleport.getEntityId())
					.x(teleport.getX())
					.y(teleport.getY())
					.z(teleport.getZ())
					.build(), turnedTo(teleport.getEntityId(), teleport.getYaw(), teleport.getPitch()));
			case ClientboundSetEntityDataPacket data -> dataChanged(data.getEntityId(), data.getMetadata());
			case ClientboundSetPassengersPacket passengers -> List.of(new PassengersChanged(passengers.getEntityId(),
					Arrays.stream(passengers.getPassengerIds()).boxed().toList()));
			case ClientboundRemoveEntitiesPacket removed -> List.of(new EntitiesRemoved(Arrays.stream(removed.getEntityIds()).boxed().toList()));
			default -> List.of();
		};
	}

	private static @Nullable EntityPacket sent(Packet packet) {
		if (packet instanceof ServerboundMovePlayerPosPacket move) return playerAt(move.getX(), move.getY(), move.getZ());
		if (packet instanceof ServerboundMovePlayerPosRotPacket move) return playerAt(move.getX(), move.getY(), move.getZ());

		return null;
	}

	private static PlayerMoved playerAt(double x, double y, double z) {
		return PlayerMoved.builder().x(x).y(y).z(z).build();
	}

	private static EntityMoved movedBy(int entityId, double x, double y, double z) {
		return EntityMoved.builder()
				.entityId(entityId)
				.x(x)
				.y(y)
				.z(z)
				.relativeX(true)
				.relativeY(true)
				.relativeZ(true)
				.build();
	}

	private static EntityTurned turnedTo(int entityId, float yaw, float pitch) {
		return EntityTurned.builder().entityId(entityId).yaw(yaw).pitch(pitch).build();
	}

	private static List<EntityPacket> dataChanged(int entityId, EntityMetadata<?, ?>[] metadata) {
		List<EntityData> values = new ArrayList<>();
		for (EntityMetadata<?, ?> entry : metadata) {
			EntityData value = data(entry);
			if (value != null) values.add(value);
		}
		return values.isEmpty() ? List.of() : List.of(new EntityDataChanged(entityId, values));
	}

	private static @Nullable EntityData data(EntityMetadata<?, ?> entry) {
		int index = entry.getId();
		Object value = entry.getValue();
		if (index == SHARED_FLAGS && value instanceof Byte flags) return SharedFlags.of(flags);
		if (index == CUSTOM_NAME && value instanceof Optional<?> name)
			return new CustomName(name.map(component -> flatten((Component) component)).orElse(null));
		if (index == CUSTOM_NAME_VISIBLE && value instanceof Boolean visible) return new CustomNameVisible(visible);
		if (index == SHOWN_ITEM && entry.getType() == MetadataType.ITEM)
			return new ShownItem(item((ItemStack) value));

		return null;
	}

	private static @Nullable EntityItem item(@Nullable ItemStack stack) {
		return stack == null || stack.getAmount() <= 0 ? null : new EntityItem(stack.getId(), stack.getAmount());
	}

	private static String flatten(Component component) {
		StringBuilder text = new StringBuilder();
		append(component, text);
		return text.toString();
	}

	/**
	 * Appends the content of text parts, the key name of keybind parts and the pattern of selector parts and, for a
	 * translated part, its key followed by each argument after a space. Score and NBT parts contribute nothing.
	 */
	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof KeybindComponent value) text.append(value.keybind());
		if (component instanceof SelectorComponent value) text.append(value.pattern());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (Component argument : value.args()) {
				text.append(' ');
				append(argument, text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
