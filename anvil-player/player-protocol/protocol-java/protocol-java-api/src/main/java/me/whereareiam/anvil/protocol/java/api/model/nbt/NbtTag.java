package me.whereareiam.anvil.protocol.java.api.model.nbt;

/**
 * One value of the game's named binary tag format, NBT, as the protocol carries it where a packet holds NBT, such as
 * a dialog or the entries of a synchronized registry. Every tag type of the game has one immutable class: the numbers
 * ({@link NbtNumber}), {@link NbtString}, the arrays {@link NbtByteArray}, {@link NbtIntArray} and
 * {@link NbtLongArray}, {@link NbtList} and {@link NbtCompound}.
 *
 * <p>A reader distinguishes the types by pattern matching and reads a compound's entries through its typed lookups:</p>
 *
 * <pre>{@code
 * String title = dialog.string("title").map(NbtString::getValue).orElse("");
 * }</pre>
 */
public sealed interface NbtTag
		permits NbtNumber, NbtString, NbtByteArray, NbtIntArray, NbtLongArray, NbtList, NbtCompound {
}
