package me.whereareiam.anvil.capability.entities.packet.model.data;

import lombok.Builder;
import lombok.Value;

/**
 * The flags every entity shares in one byte of its data: on fire ({@code 0x01}), sneaking ({@code 0x02}),
 * sprinting ({@code 0x08}), swimming ({@code 0x10}), invisible ({@code 0x20}), glowing ({@code 0x40}) and gliding
 * with elytra ({@code 0x80}).
 */
@Value
@Builder
public class SharedFlags implements EntityData {
	boolean onFire;
	boolean sneaking;
	boolean sprinting;
	boolean swimming;
	boolean invisible;
	boolean glowing;
	boolean gliding;

	/**
	 * Reads the flags from the byte the server sends.
	 *
	 * @param flags the shared flags byte
	 * @return the flags
	 */
	public static SharedFlags of(byte flags) {
		return SharedFlags.builder()
				.onFire((flags & 0x01) != 0)
				.sneaking((flags & 0x02) != 0)
				.sprinting((flags & 0x08) != 0)
				.swimming((flags & 0x10) != 0)
				.invisible((flags & 0x20) != 0)
				.glowing((flags & 0x40) != 0)
				.gliding((flags & 0x80) != 0)
				.build();
	}
}
