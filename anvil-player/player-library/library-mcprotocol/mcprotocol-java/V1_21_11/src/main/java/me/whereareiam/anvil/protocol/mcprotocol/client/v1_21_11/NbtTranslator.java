package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByteArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtDouble;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtIntArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtLong;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtLongArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtShort;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between the NBT tree MCProtocolLib reads and writes, Cloudburst's {@link NbtMap} with its boxed values,
 * and the protocol's {@link NbtTag}, tag by tag.
 *
 * <p>A Cloudburst list holds one element type. Since Minecraft 1.21.5 the game writes a list of mixed types as a list
 * of compounds, wrapping each element that is not a compound, or is itself such a wrapper, in a compound with the
 * empty key; the translation unwraps those elements when reading and wraps them again when writing such a list.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NbtTranslator {
	private static final String WRAPPER_KEY = "";

	/**
	 * Translates a compound MCProtocolLib read.
	 *
	 * @param compound the library's compound
	 * @return the protocol's compound
	 */
	static @NotNull NbtCompound compound(@NotNull NbtMap compound) {
		Map<String, NbtTag> entries = new LinkedHashMap<>();
		compound.forEach((key, value) -> entries.put(key, tag(value)));

		return new NbtCompound(entries);
	}

	/**
	 * Translates one value MCProtocolLib read.
	 *
	 * @param value the library's value: a boxed number, a string, a primitive array, a list or a compound
	 * @return the protocol's tag
	 * @throws IllegalArgumentException when the value is of no NBT type
	 */
	static @NotNull NbtTag tag(@NotNull Object value) {
		return switch (value) {
			case Byte number -> new NbtByte(number);
			case Short number -> new NbtShort(number);
			case Integer number -> new NbtInt(number);
			case Long number -> new NbtLong(number);
			case Float number -> new NbtFloat(number);
			case Double number -> new NbtDouble(number);
			case String text -> new NbtString(text);
			case byte[] values -> new NbtByteArray(values);
			case int[] values -> new NbtIntArray(values);
			case long[] values -> new NbtLongArray(values);
			case org.cloudburstmc.nbt.NbtList<?> list -> list(list);
			case NbtMap compound -> compound(compound);
			default -> throw new IllegalArgumentException("Not an NBT value: " + value.getClass().getName());
		};
	}

	/**
	 * Translates a compound for MCProtocolLib to write.
	 *
	 * @param compound the protocol's compound
	 * @return the library's compound
	 */
	static @NotNull NbtMap map(@NotNull NbtCompound compound) {
		NbtMapBuilder map = NbtMap.builder();
		compound.getEntries().forEach((key, tag) -> map.put(key, value(tag)));

		return map.build();
	}

	/**
	 * Translates one tag for MCProtocolLib to write.
	 *
	 * @param tag the protocol's tag
	 * @return the library's value
	 */
	static @NotNull Object value(@NotNull NbtTag tag) {
		return switch (tag) {
			case NbtByte number -> number.getValue();
			case NbtShort number -> number.getValue();
			case NbtInt number -> number.getValue();
			case NbtLong number -> number.getValue();
			case NbtFloat number -> number.getValue();
			case NbtDouble number -> number.getValue();
			case NbtString text -> text.getValue();
			case NbtByteArray array -> array.getValues();
			case NbtIntArray array -> array.getValues();
			case NbtLongArray array -> array.getValues();
			case NbtList list -> list(list);
			case NbtCompound compound -> map(compound);
		};
	}

	private static NbtList list(org.cloudburstmc.nbt.NbtList<?> list) {
		List<NbtTag> elements = new ArrayList<>();
		for (Object element : list) elements.add(tag(unwrapped(element)));

		return new NbtList(elements);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static org.cloudburstmc.nbt.NbtList<?> list(NbtList list) {
		if (list.getElements().isEmpty()) return org.cloudburstmc.nbt.NbtList.EMPTY;

		List<Object> values = new ArrayList<>();
		for (NbtTag element : list.getElements()) values.add(value(element));
		if (values.stream().map(Object::getClass).distinct().count() == 1)
			return new org.cloudburstmc.nbt.NbtList(NbtType.byClass(values.getFirst().getClass()), values);

		List<NbtMap> wrapped = values.stream()
				.map(NbtTranslator::wrapped)
				.toList();
		return new org.cloudburstmc.nbt.NbtList<>(NbtType.COMPOUND, wrapped);
	}

	private static Object unwrapped(Object element) {
		if (element instanceof NbtMap compound && wrapper(compound)) return compound.get(WRAPPER_KEY);

		return element;
	}

	private static NbtMap wrapped(Object value) {
		if (value instanceof NbtMap compound && !wrapper(compound)) return compound;

		NbtMapBuilder wrapper = NbtMap.builder();
		wrapper.put(WRAPPER_KEY, value);

		return wrapper.build();
	}

	private static boolean wrapper(NbtMap compound) {
		return compound.size() == 1 && compound.containsKey(WRAPPER_KEY);
	}
}
