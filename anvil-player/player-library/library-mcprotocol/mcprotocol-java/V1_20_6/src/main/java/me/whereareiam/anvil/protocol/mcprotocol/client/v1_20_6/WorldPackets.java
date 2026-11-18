package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.DemoMessageValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ElderGuardianEffectValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.EnterCreditsValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEventValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.LimitedCraftingValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RespawnScreenValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ThunderStrengthValue;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The world of the MCProtocolLib releases from 1.20.5 until 1.21.1: their time, their game events, which these
 * releases keep with each event's number as a value of their own that the translation turns back into the game's
 * number, and their spawn point with the angle a player spawns facing. These releases send a negative time of day
 * while the time of day stands still.
 */
public final class WorldPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSetTime.class, ClientboundGameEvent.class, ClientboundSetDefaultSpawnPosition.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundSetTimePacket time -> ClientboundSetTime.builder()
					.gameTime(time.getWorldAge())
					.dayTime(Math.abs(time.getTime()))
					.tickDayTime(time.getTime() >= 0)
					.build();
			case ClientboundGameEventPacket event -> event(event);
			case ClientboundSetDefaultSpawnPositionPacket spawn -> ClientboundSetDefaultSpawnPosition.builder()
					.position(position(spawn.getPosition()))
					.yaw(spawn.getAngle())
					.build();
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Translates a game event, which these releases keep with the event's number as a value of their own.
	 */
	private static @NotNull ClientboundGameEvent event(@NotNull ClientboundGameEventPacket event) {
		GameEventType type = switch (event.getNotification()) {
			case INVALID_BED -> GameEventType.NO_RESPAWN_BLOCK_AVAILABLE;
			case START_RAIN -> GameEventType.START_RAINING;
			case STOP_RAIN -> GameEventType.STOP_RAINING;
			case CHANGE_GAMEMODE -> GameEventType.CHANGE_GAME_MODE;
			case ENTER_CREDITS -> GameEventType.WIN_GAME;
			case DEMO_MESSAGE -> GameEventType.DEMO_EVENT;
			case ARROW_HIT_PLAYER -> GameEventType.ARROW_HIT_PLAYER;
			case RAIN_STRENGTH -> GameEventType.RAIN_LEVEL_CHANGE;
			case THUNDER_STRENGTH -> GameEventType.THUNDER_LEVEL_CHANGE;
			case PUFFERFISH_STING_SOUND -> GameEventType.PUFFER_FISH_STING;
			case AFFECTED_BY_ELDER_GUARDIAN -> GameEventType.GUARDIAN_ELDER_EFFECT;
			case ENABLE_RESPAWN_SCREEN -> GameEventType.IMMEDIATE_RESPAWN;
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

	private static @NotNull BlockPosition position(@NotNull Vector3i position) {
		return BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build();
	}
}
