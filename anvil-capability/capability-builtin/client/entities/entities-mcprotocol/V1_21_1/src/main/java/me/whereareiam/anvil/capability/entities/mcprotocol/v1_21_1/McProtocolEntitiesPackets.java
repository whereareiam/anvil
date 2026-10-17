package me.whereareiam.anvil.capability.entities.mcprotocol.v1_21_1;

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
import me.whereareiam.anvil.capability.entities.packet.model.data.DisplayText;
import me.whereareiam.anvil.capability.entities.packet.model.data.EntityData;
import me.whereareiam.anvil.capability.entities.packet.model.data.SharedFlags;
import me.whereareiam.anvil.capability.entities.packet.model.data.ShownItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Entity packets of the MCProtocolLib release for Minecraft 1.21.1, the first one in the
 * {@code org.geysermc.mcprotocollib} packages. Experience orbs still spawn through their own packet, without a unique
 * ID or rotation, teleports place and turn an entity absolutely, and only the player's position names relative
 * coordinates.
 *
 * <p>Entity data of every entity keeps its shared flags at index 0, the optional custom name at 2 and whether it is
 * shown at 3. Dropped items, item frames, thrown items and fireworks keep the item they show at 8. A display keeps
 * fifteen values from index 8, so a text display's text and an item display's item are at 23. The segment reads
 * these by index and value type without knowing the entity's type: no other entity has an item at 8 or 23, or a
 * component at 23.</p>
 */
public final class McProtocolEntitiesPackets implements EntitiesPackets<Session> {
	private static final int SHARED_FLAGS = 0;
	private static final int CUSTOM_NAME = 2;
	private static final int CUSTOM_NAME_VISIBLE = 3;
	private static final int SHOWN_ITEM = 8;
	private static final int DISPLAYED = 23;

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
			case ClientboundStartConfigurationPacket ignored -> List.of(new WorldCleared());
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
		if (index == DISPLAYED && value instanceof Component text) return new DisplayText(flatten(text));
		if ((index == SHOWN_ITEM || index == DISPLAYED) && entry.getType() == MetadataType.ITEM)
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
			for (TranslationArgument argument : value.arguments()) {
				text.append(' ');
				append(argument.asComponent(), text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
