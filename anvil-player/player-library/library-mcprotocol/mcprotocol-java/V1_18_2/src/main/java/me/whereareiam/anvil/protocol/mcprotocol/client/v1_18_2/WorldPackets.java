package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.level.notify.DemoMessageValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.EnterCreditsValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEventValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.RainStrengthValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.RespawnScreenValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.ThunderStrengthValue;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The game events of the MCProtocolLib 1.18.2 and 1.19.4 releases. These releases keep each event's number as a value
 * of their own, which the translation turns back into the game's number; they read no value for the elder guardian's
 * effect, which is therefore {@code 0}, and only 1.19.4 knows the demo's screenshot message.
 */
public final class WorldPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundGameEvent.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundGameEventPacket event)) return null;

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
		};
		return new ClientboundGameEvent(type, value(event.getValue()));
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
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
