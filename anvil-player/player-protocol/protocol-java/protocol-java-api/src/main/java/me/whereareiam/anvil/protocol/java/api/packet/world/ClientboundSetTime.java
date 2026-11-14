package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.ClockState;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The server tells the player the time of its world, when the player joins a level and then about once a second.
 * Releases before Minecraft 26.1 send the time of day of the level the player is in; from 26.1 the server sends its
 * world clocks instead, and the time of day of a level is the time of the clock its dimension type names as its
 * {@linkplain me.whereareiam.anvil.protocol.java.api.model.world.DimensionType#getDefaultClock() default clock}.
 */
@Value
@Builder
public class ClientboundSetTime implements ClientboundPacket {
	/**
	 * The age of the world in ticks, which keeps advancing whatever the time of day does.
	 */
	long gameTime;
	/**
	 * The time of day of the player's level in ticks since the world began, where each day lasts 24000 ticks, or
	 * {@code null} from Minecraft 26.1, which sends {@link #getClockUpdates() clocks} instead. Releases before
	 * Minecraft 1.21.2 send a negative time while the time of day stands still, which every release reports as the
	 * time itself with {@link #isTickDayTime()} false.
	 */
	@Nullable Long dayTime;
	/**
	 * Whether the client advances the time of day itself between updates; always {@code false} from Minecraft 26.1,
	 * whose clocks carry their own {@linkplain ClockState#getRate() rate}.
	 */
	boolean tickDayTime;
	/**
	 * The state of each world clock the server updates, by the clock's key, such as {@code minecraft:overworld};
	 * empty before Minecraft 26.1.
	 */
	@NotNull @Singular Map<String, ClockState> clockUpdates;
}
