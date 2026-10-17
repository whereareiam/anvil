package me.whereareiam.anvil.capability.dialogs.mcprotocol.v1_21_11;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the NBT values MCProtocolLib reads as SNBT and reads SNBT compounds back, through adventure's SNBT
 * reader and writer, which the release ships.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NbtText {
	static String write(Object tag) {
		try {
			return TagStringIO.tagStringIO().asString(adventure(tag));
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	static NbtMap compound(String snbt) {
		try {
			return (NbtMap) cloudburst(TagStringIO.tagStringIO().asCompound(snbt));
		} catch (IOException failure) {
			throw new IllegalArgumentException("Not an SNBT compound: " + snbt, failure);
		}
	}

	private static BinaryTag adventure(Object tag) {
		return switch (tag) {
			case Byte value -> ByteBinaryTag.byteBinaryTag(value);
			case Short value -> ShortBinaryTag.shortBinaryTag(value);
			case Integer value -> IntBinaryTag.intBinaryTag(value);
			case Long value -> LongBinaryTag.longBinaryTag(value);
			case Float value -> FloatBinaryTag.floatBinaryTag(value);
			case Double value -> DoubleBinaryTag.doubleBinaryTag(value);
			case String value -> StringBinaryTag.stringBinaryTag(value);
			case byte[] value -> ByteArrayBinaryTag.byteArrayBinaryTag(value);
			case int[] value -> IntArrayBinaryTag.intArrayBinaryTag(value);
			case long[] value -> LongArrayBinaryTag.longArrayBinaryTag(value);
			case NbtList<?> list -> {
				List<BinaryTag> elements = new ArrayList<>();
				for (Object element : list) elements.add(adventure(element));
				yield ListBinaryTag.from(elements);
			}
			case NbtMap map -> {
				CompoundBinaryTag.Builder compound = CompoundBinaryTag.builder();
				map.forEach((key, value) -> compound.put(key, adventure(value)));
				yield compound.build();
			}
			default -> throw new IllegalArgumentException("Unknown NBT value " + tag.getClass().getName());
		};
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object cloudburst(BinaryTag tag) {
		return switch (tag) {
			case ByteBinaryTag value -> value.value();
			case ShortBinaryTag value -> value.value();
			case IntBinaryTag value -> value.value();
			case LongBinaryTag value -> value.value();
			case FloatBinaryTag value -> value.value();
			case DoubleBinaryTag value -> value.value();
			case StringBinaryTag value -> value.value();
			case ByteArrayBinaryTag value -> value.value();
			case IntArrayBinaryTag value -> value.value();
			case LongArrayBinaryTag value -> value.value();
			case ListBinaryTag list -> {
				if (list.size() == 0) yield NbtList.EMPTY;

				List<Object> elements = new ArrayList<>();
				for (BinaryTag element : list) elements.add(cloudburst(element));
				yield new NbtList(NbtType.byClass(elements.getFirst().getClass()), elements);
			}
			case CompoundBinaryTag compound -> {
				NbtMapBuilder map = NbtMap.builder();
				for (String key : compound.keySet()) map.put(key, cloudburst(compound.get(key)));
				yield map.build();
			}
			default -> throw new IllegalArgumentException("Unknown NBT tag " + tag.type());
		};
	}
}
