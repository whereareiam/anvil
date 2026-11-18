package me.whereareiam.anvil.edition.java.protocol.packet.player;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;

/**
 * The player starts or stops flying on its own, as the vanilla client tells the server when its player toggles flight
 * by pressing jump twice, lands while flying, or flies again as a spectator ({@code LocalPlayer.onUpdateAbilities}).
 * From Minecraft 1.16 the packet carries only whether the player flies; the server keeps every other ability it
 * granted.
 *
 * <pre>{@code
 * protocol.send(new ServerboundPlayerAbilities(false));
 * }</pre>
 */
@Value
public class ServerboundPlayerAbilities implements ServerboundPacket {
	/**
	 * Whether the player flies now.
	 */
	boolean flying;
}
