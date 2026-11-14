package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import org.jetbrains.annotations.NotNull;

/**
 * One of the game's small state changes, such as the start of rain, a new game mode or whether the client shows the
 * death screen, with the number the event carries.
 */
@Value
public class ClientboundGameEvent implements ClientboundPacket {
	/**
	 * Which state changes.
	 */
	@NotNull GameEventType event;
	/**
	 * The event's value as the game sends it: the game mode's ID for {@link GameEventType#CHANGE_GAME_MODE}, {@code 1}
	 * for an immediate respawn and {@code 0} for the death screen with {@link GameEventType#IMMEDIATE_RESPAWN}, the
	 * level from {@code 0} to {@code 1} with rain and thunder levels, the message ID with
	 * {@link GameEventType#DEMO_EVENT}, {@code 1} for the first time with {@link GameEventType#WIN_GAME}, {@code 1} for
	 * an audible effect with {@link GameEventType#GUARDIAN_ELDER_EFFECT}, {@code 1} for limited crafting with
	 * {@link GameEventType#LIMITED_CRAFTING}, and {@code 0} for the other events.
	 */
	float value;
}
