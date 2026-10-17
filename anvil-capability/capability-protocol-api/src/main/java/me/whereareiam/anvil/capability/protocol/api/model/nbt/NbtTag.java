package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import org.jetbrains.annotations.NotNull;

/**
 * One Minecraft NBT tag, independent of any protocol library. Packet capabilities carry NBT across their release
 * ports in this model: a library segment converts its native tags at the boundary, and the library-neutral binding
 * reads and builds values with these types. The model is closed and immutable: numbers ({@link NbtNumber}),
 * strings, the three array types, lists of one element type and compounds whose entries keep their order.
 *
 * <pre>{@code
 * NbtCompound payload = NbtCompound.builder()
 *         .entry("source", NbtString.of("dialog"))
 *         .entry("remember", NbtByte.of(true))
 *         .build();
 * String snbt = payload.toSnbt(); // {source:"dialog",remember:1b}
 * }</pre>
 */
public sealed interface NbtTag permits NbtNumber, NbtString, NbtByteArray, NbtIntArray, NbtLongArray, NbtList, NbtCompound {
	/**
	 * Writes the tag as compact SNBT, as {@link Snbt#write(NbtTag)} describes.
	 *
	 * @return the tag as SNBT
	 */
	default @NotNull String toSnbt() {
		return Snbt.write(this);
	}
}
