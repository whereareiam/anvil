package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotificationValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.DemoMessageValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.EnterCreditsValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.RainStrengthValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.RespawnScreenValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.ThunderStrengthValue;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUpdateTimePacket;
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
 * The world of the MCProtocolLib releases from 1.16.5 until 1.17.1: their game events, which these releases keep with
 * each event's number as a value of their own that the translation turns back into the game's number, and their time.
 * They read no value for the elder guardian's effect, which is therefore {@code 0}, name the rain events the other
 * way round from the game, and send a negative time of day while the time of day stands still. Minecraft 1.17 split
 * the world border into a packet per change and gave the spawn point an angle, so each release's
 * {@code WorldPackets} reads those two; both move the border in milliseconds, which it reports as ticks.
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
		if (packet instanceof ServerUpdateTimePacket time)
			return ClientboundSetTime.builder()
					.gameTime(time.getWorldAge())
					.dayTime(Math.abs(time.getTime()))
					.tickDayTime(time.getTime() >= 0)
					.build();
		if (!(packet instanceof ServerNotifyClientPacket event)) return borderOrSpawn(packet);

		GameEventType type = switch (event.getNotification()) {
			case INVALID_BED -> GameEventType.NO_RESPAWN_BLOCK_AVAILABLE;
			// The library names the rain events by their meaning before Minecraft named them: its STOP_RAIN is the
			// game's START_RAINING, number 1, and its START_RAIN the game's STOP_RAINING, number 2.
			case STOP_RAIN -> GameEventType.START_RAINING;
			case START_RAIN -> GameEventType.STOP_RAINING;
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

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Translates the release's world border or spawn point.
	 *
	 * @param packet the release's packet
	 * @return the border or spawn point packet, or {@code null} for any other packet
	 */
	protected abstract @Nullable ClientboundPacket borderOrSpawn(@NotNull Packet packet);

	/**
	 * Converts the milliseconds a border of these releases moves for into ticks.
	 *
	 * @param millis the milliseconds the release sent
	 * @return the ticks of 50 milliseconds
	 */
	protected static long ticks(long millis) {
		return millis / 50;
	}

	/**
	 * Translates the block of a spawn point.
	 *
	 * @param position the library's position
	 * @return the block's coordinates
	 */
	protected static @NotNull BlockPosition position(@NotNull Position position) {
		return BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build();
	}

	/**
	 * Returns the number the game sent for an event's value.
	 */
	private static float value(@Nullable ClientNotificationValue value) {
		return switch (value) {
			case GameMode mode -> switch (mode) {
				case SURVIVAL -> 0F;
				case CREATIVE -> 1F;
				case ADVENTURE -> 2F;
				case SPECTATOR -> 3F;
				case UNKNOWN -> -1F;
			};
			case RespawnScreenValue respawn -> respawn == RespawnScreenValue.IMMEDIATE_RESPAWN ? 1F : 0F;
			case EnterCreditsValue credits -> credits == EnterCreditsValue.FIRST_TIME ? 1F : 0F;
			case DemoMessageValue demo -> switch (demo) {
				case WELCOME -> 0F;
				case MOVEMENT_CONTROLS -> 101F;
				case JUMP_CONTROL -> 102F;
				case INVENTORY_CONTROL -> 103F;
			};
			case RainStrengthValue rain -> rain.getStrength();
			case ThunderStrengthValue thunder -> thunder.getStrength();
			case null, default -> 0F;
		};
	}
}
