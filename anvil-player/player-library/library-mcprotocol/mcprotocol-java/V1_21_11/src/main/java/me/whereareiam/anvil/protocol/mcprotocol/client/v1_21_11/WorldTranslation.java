package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.DemoMessageValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ElderGuardianEffectValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.EnterCreditsValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEventValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.LimitedCraftingValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RespawnScreenValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ThunderStrengthValue;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The world of the MCProtocolLib releases from 1.21.11 on: their game events, which these releases name as the game
 * does and keep with each event's number as a value of their own that the translation turns back into the game's
 * number, their world border, which they move in
 * ticks, their spawn point in a level of its own with the view a player spawns with, and their time, which each
 * release's {@code WorldPackets} reads, because Minecraft 26.1 replaced the time of day with world clocks. A release
 * whose time refers to its configuration's registries reads them as they arrive and leaves the registry data itself to
 * the configuration translation; one translation serves one session.
 */
public abstract class WorldTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSetTime.class, ClientboundGameEvent.class, ClientboundInitializeBorder.class,
				ClientboundSetBorderCenter.class, ClientboundSetBorderLerpSize.class, ClientboundSetBorderSize.class,
				ClientboundSetBorderWarningDelay.class, ClientboundSetBorderWarningDistance.class,
				ClientboundSetDefaultSpawnPosition.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundRegistryDataPacket registry -> {
				registry(registry);
				yield null;
			}
			case ClientboundSetTimePacket time -> time(time);
			case ClientboundGameEventPacket event -> event(event);
			case ClientboundInitializeBorderPacket border -> ClientboundInitializeBorder.builder()
					.centerX(border.getNewCenterX())
					.centerZ(border.getNewCenterZ())
					.oldSize(border.getOldSize())
					.newSize(border.getNewSize())
					.lerpTime(border.getLerpTime())
					.absoluteMaxSize(border.getNewAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build();
			case ClientboundSetBorderCenterPacket center -> new ClientboundSetBorderCenter(center.getNewCenterX(), center.getNewCenterZ());
			case ClientboundSetBorderLerpSizePacket lerp -> new ClientboundSetBorderLerpSize(lerp.getOldSize(), lerp.getNewSize(),
					lerp.getLerpTime());
			case ClientboundSetBorderSizePacket size -> new ClientboundSetBorderSize(size.getSize());
			case ClientboundSetBorderWarningDelayPacket delay -> new ClientboundSetBorderWarningDelay(delay.getWarningDelay());
			case ClientboundSetBorderWarningDistancePacket distance -> new ClientboundSetBorderWarningDistance(distance.getWarningBlocks());
			case ClientboundSetDefaultSpawnPositionPacket spawn -> spawn(spawn);
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Translates the time of the world.
	 *
	 * @param time the release's time
	 * @return the time every release reports
	 */
	protected abstract @NotNull ClientboundSetTime time(@NotNull ClientboundSetTimePacket time);

	/**
	 * Reads a registry the configuration synchronized, which a release whose time refers to one remembers.
	 *
	 * @param registry the release's registry data
	 */
	protected void registry(@NotNull ClientboundRegistryDataPacket registry) {
	}

	private static @NotNull ClientboundSetDefaultSpawnPosition spawn(@NotNull ClientboundSetDefaultSpawnPositionPacket spawn) {
		GlobalPos point = spawn.getGlobalPos();
		Vector3i position = point.getPosition();
		return ClientboundSetDefaultSpawnPosition.builder()
				.dimension(point.getDimension().asString())
				.position(BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build())
				.yaw(spawn.getYaw())
				.pitch(spawn.getPitch())
				.build();
	}

	/**
	 * Translates a game event, which these releases keep with the event's number as a value of their own.
	 */
	private static @NotNull ClientboundGameEvent event(@NotNull ClientboundGameEventPacket event) {
		GameEventType type = switch (event.getNotification()) {
			case NO_RESPAWN_BLOCK_AVAILABLE -> GameEventType.NO_RESPAWN_BLOCK_AVAILABLE;
			case START_RAINING -> GameEventType.START_RAINING;
			case STOP_RAINING -> GameEventType.STOP_RAINING;
			case CHANGE_GAME_MODE -> GameEventType.CHANGE_GAME_MODE;
			case WIN_GAME -> GameEventType.WIN_GAME;
			case DEMO_EVENT -> GameEventType.DEMO_EVENT;
			case PLAY_ARROW_HIT_SOUND -> GameEventType.ARROW_HIT_PLAYER;
			case RAIN_LEVEL_CHANGE -> GameEventType.RAIN_LEVEL_CHANGE;
			case THUNDER_LEVEL_CHANGE -> GameEventType.THUNDER_LEVEL_CHANGE;
			case PUFFER_FISH_STING -> GameEventType.PUFFER_FISH_STING;
			case GUARDIAN_ELDER_EFFECT -> GameEventType.GUARDIAN_ELDER_EFFECT;
			case IMMEDIATE_RESPAWN -> GameEventType.IMMEDIATE_RESPAWN;
			case LIMITED_CRAFTING -> GameEventType.LIMITED_CRAFTING;
			case LEVEL_CHUNKS_LOAD_START -> GameEventType.LEVEL_CHUNKS_LOAD_START;
		};
		return new ClientboundGameEvent(type, value(event.getValue()));
	}

	/**
	 * Returns the number the game sent for an event's value.
	 */
	private static float value(@Nullable GameEventValue value) {
		return switch (value) {
			case GameMode mode -> mode.ordinal();
			case RespawnScreenValue respawn -> respawn == RespawnScreenValue.IMMEDIATE_RESPAWN ? 1F : 0F;
			case EnterCreditsValue credits -> credits == EnterCreditsValue.FIRST_TIME ? 1F : 0F;
			case DemoMessageValue demo -> demo.getId();
			case RainStrengthValue rain -> rain.getStrength();
			case ThunderStrengthValue thunder -> thunder.getStrength();
			case ElderGuardianEffectValue effect -> effect.isAudible() ? 1F : 0F;
			case LimitedCraftingValue crafting -> crafting == LimitedCraftingValue.LIMITED_CRAFTING ? 1F : 0F;
			case null, default -> 0F;
		};
	}
}
