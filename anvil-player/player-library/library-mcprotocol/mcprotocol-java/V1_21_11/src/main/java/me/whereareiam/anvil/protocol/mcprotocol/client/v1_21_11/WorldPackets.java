package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The game events of the MCProtocolLib releases from 1.21.11 on, which name the events as the game does and keep
 * each event's number as a value of their own; the translation turns it back into the game's number.
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

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
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
