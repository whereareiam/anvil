package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import org.jetbrains.annotations.NotNull;

/**
 * A numeric NBT tag. Minecraft reads numbers leniently, so a value written as one numeric type is often read as
 * another; {@link #asNumber()} gives the value for such conversions.
 *
 * <pre>{@code
 * if (compound.get("max_length") instanceof NbtNumber length) maxLength = length.asNumber().intValue();
 * }</pre>
 */
public sealed interface NbtNumber extends NbtTag permits NbtByte, NbtShort, NbtInt, NbtLong, NbtFloat, NbtDouble {
	/**
	 * Returns the tag's value boxed as its Java number type.
	 *
	 * @return the value
	 */
	@NotNull Number asNumber();
}
