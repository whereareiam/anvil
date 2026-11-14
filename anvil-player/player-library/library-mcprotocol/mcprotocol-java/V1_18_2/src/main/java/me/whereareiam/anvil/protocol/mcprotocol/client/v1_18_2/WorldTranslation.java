package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.level.notify.DemoMessageValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.EnterCreditsValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEvent;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEventValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.RainStrengthValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.RespawnScreenValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.ThunderStrengthValue;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import com.github.steveice10.packetlib.packet.Packet;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The world of the MCProtocolLib releases from 1.18.2 until 1.19.4: their time, their game events, their world border
 * and their spawn point. These releases keep each game event's number as a value of their own, which the translation
 * turns back into the game's number; they read no value for the elder guardian's effect, which is therefore
 * {@code 0}, and only 1.19.4 knows the demo's screenshot message. Each release's {@code WorldPackets} reads the spawn point's position and tells the rain events
 * apart, because MCProtocolLib 1.19 changed the position's type and renumbered the events. These releases send a negative time of day while the time of day stands still, and move the border in
 * milliseconds.
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
			case ClientboundSetTimePacket time -> ClientboundSetTime.builder()
					.gameTime(time.getWorldAge())
					.dayTime(Math.abs(time.getTime()))
					.tickDayTime(time.getTime() >= 0)
					.build();
			case ClientboundGameEventPacket event -> event(event);
			case ClientboundInitializeBorderPacket border -> ClientboundInitializeBorder.builder()
					.centerX(border.getNewCenterX())
					.centerZ(border.getNewCenterZ())
					.oldSize(border.getOldSize())
					.newSize(border.getNewSize())
					.lerpTime(ticks(border.getLerpTime()))
					.absoluteMaxSize(border.getNewAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build();
			case ClientboundSetBorderCenterPacket center -> new ClientboundSetBorderCenter(center.getNewCenterX(), center.getNewCenterZ());
			case ClientboundSetBorderLerpSizePacket lerp -> new ClientboundSetBorderLerpSize(lerp.getOldSize(), lerp.getNewSize(),
					ticks(lerp.getLerpTime()));
			case ClientboundSetBorderSizePacket size -> new ClientboundSetBorderSize(size.getSize());
			case ClientboundSetBorderWarningDelayPacket delay -> new ClientboundSetBorderWarningDelay(delay.getWarningDelay());
			case ClientboundSetBorderWarningDistancePacket distance -> new ClientboundSetBorderWarningDistance(distance.getWarningBlocks());
			case ClientboundSetDefaultSpawnPositionPacket spawn -> ClientboundSetDefaultSpawnPosition.builder()
					.position(position(spawn))
					.yaw(spawn.getAngle())
					.build();
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Tells which of the game's rain events the library's event is, because MCProtocolLib 1.19 renumbered them.
	 *
	 * @param event the library's {@code START_RAIN} or {@code STOP_RAIN}
	 * @return the game's event of the same ID
	 */
	protected abstract @NotNull GameEventType rain(@NotNull GameEvent event);

	/**
	 * Reads the block of a spawn point.
	 *
	 * @param spawn the release's spawn point
	 * @return the block's coordinates
	 */
	protected abstract @NotNull BlockPosition position(@NotNull ClientboundSetDefaultSpawnPositionPacket spawn);

	/**
	 * Translates a game event, which these releases keep with the event's number as a value of their own.
	 */
	private @NotNull ClientboundGameEvent event(@NotNull ClientboundGameEventPacket event) {
		GameEventType type = switch (event.getNotification()) {
			case INVALID_BED -> GameEventType.NO_RESPAWN_BLOCK_AVAILABLE;
			case START_RAIN, STOP_RAIN -> rain(event.getNotification());
			case CHANGE_GAMEMODE -> GameEventType.CHANGE_GAME_MODE;
			case ENTER_CREDITS -> GameEventType.WIN_GAME;
			case DEMO_MESSAGE -> GameEventType.DEMO_EVENT;
			case ARROW_HIT_PLAYER -> GameEventType.ARROW_HIT_PLAYER;
			case RAIN_STRENGTH -> GameEventType.RAIN_LEVEL_CHANGE;
			case THUNDER_STRENGTH -> GameEventType.THUNDER_LEVEL_CHANGE;
			case PUFFERFISH_STING_SOUND -> GameEventType.PUFFER_FISH_STING;
			case AFFECTED_BY_ELDER_GUARDIAN -> GameEventType.GUARDIAN_ELDER_EFFECT;
			case ENABLE_RESPAWN_SCREEN -> GameEventType.IMMEDIATE_RESPAWN;
		};
		return new ClientboundGameEvent(type, value(event.getValue()));
	}

	/**
	 * Converts the milliseconds a border of these releases moves for into ticks.
	 */
	private static long ticks(long millis) {
		return millis / 50;
	}

	/**
	 * Returns the number the game sent for an event's value. The game modes and demo messages are named, not
	 * switched over, because the releases differ in them: 1.18.2 has an unknown game mode and 1.19.4 a screenshot
	 * message.
	 */
	private static float value(@Nullable GameEventValue value) {
		return switch (value) {
			case GameMode mode -> switch (mode.name()) {
				case "SURVIVAL" -> 0F;
				case "CREATIVE" -> 1F;
				case "ADVENTURE" -> 2F;
				case "SPECTATOR" -> 3F;
				default -> -1F;
			};
			case RespawnScreenValue respawn -> respawn == RespawnScreenValue.IMMEDIATE_RESPAWN ? 1F : 0F;
			case EnterCreditsValue credits -> credits == EnterCreditsValue.FIRST_TIME ? 1F : 0F;
			case DemoMessageValue demo -> switch (demo.name()) {
				case "WELCOME" -> 0F;
				case "MOVEMENT_CONTROLS" -> 101F;
				case "JUMP_CONTROL" -> 102F;
				case "INVENTORY_CONTROL" -> 103F;
				default -> 104F;
			};
			case RainStrengthValue rain -> rain.getStrength();
			case ThunderStrengthValue thunder -> thunder.getStrength();
			case null, default -> 0F;
		};
	}
}
