package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Translates the relative elements of a placement of the MCProtocolLib releases from 1.21.11 on, which the player's
 * teleports and the entities' position syncs carry.
 */
public final class RelativeElements {
	private RelativeElements() {
	}

	/**
	 * Names the coordinates, angles and velocity components a placement adds to the current ones.
	 *
	 * @param elements the release's relative elements
	 * @return the protocol's relatives
	 */
	public static @NotNull Set<Relative> translate(@NotNull List<PositionElement> elements) {
		Set<Relative> relatives = EnumSet.noneOf(Relative.class);
		for (PositionElement element : elements) relatives.add(switch (element) {
			case X -> Relative.X;
			case Y -> Relative.Y;
			case Z -> Relative.Z;
			case Y_ROT -> Relative.YAW;
			case X_ROT -> Relative.PITCH;
			case DELTA_X -> Relative.DELTA_X;
			case DELTA_Y -> Relative.DELTA_Y;
			case DELTA_Z -> Relative.DELTA_Z;
			case ROTATE_DELTA -> Relative.ROTATE_DELTA;
		});
		return Collections.unmodifiableSet(relatives);
	}
}
