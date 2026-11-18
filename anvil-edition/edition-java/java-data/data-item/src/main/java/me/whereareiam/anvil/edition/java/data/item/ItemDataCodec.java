package me.whereareiam.anvil.edition.java.data.item;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.data.item.model.ItemDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The shipped item data's binary form, written by the build's generator and read by {@link JavaItemData}: one class
 * for both directions, so the two can never disagree. Both ship in this module's JAR, so the data carries no format
 * version.
 *
 * <p>A registry is its protocol, its {@link ItemDataSource} and the key of each item by ID. The index lists each
 * supported protocol, with the reason when it has no data.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ItemDataCodec {
	/**
	 * Writes a registry, compressed.
	 */
	static void writeRegistry(@NotNull ItemRegistry registry, @NotNull OutputStream output) throws IOException {
		try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(output))) {
			data.writeInt(registry.protocol());
			ItemDataSource source = registry.source();
			data.writeUTF(source.getRepository());
			data.writeUTF(source.getVersion());
			data.writeUTF(source.getCommit());
			data.writeUTF(source.getItems());

			List<String> keys = registry.keys();
			data.writeInt(keys.size());
			for (String key : keys)
				data.writeUTF(key);
		}
	}

	/**
	 * Reads a registry that {@link #writeRegistry} wrote.
	 */
	static @NotNull ItemRegistry readRegistry(@NotNull InputStream input) throws IOException {
		try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
			int protocol = data.readInt();
			ItemDataSource source = ItemDataSource.builder()
					.repository(data.readUTF())
					.version(data.readUTF())
					.commit(data.readUTF())
					.items(data.readUTF())
					.build();

			List<String> keys = new ArrayList<>();
			for (int count = data.readInt(); keys.size() < count; )
				keys.add(data.readUTF());

			return new ItemRegistry(protocol, source, keys);
		}
	}

	/**
	 * Writes the index: each supported protocol, and the reason for each that has no data.
	 */
	static void writeIndex(@NotNull JavaItemData index, @NotNull OutputStream output) throws IOException {
		try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(output))) {
			data.writeInt(index.protocols().size());
			for (int protocol : index.protocols())
				data.writeInt(protocol);
			data.writeInt(index.missingProtocols().size());
			for (int protocol : index.missingProtocols()) {
				data.writeInt(protocol);
				data.writeUTF(index.missingReason(protocol));
			}
		}
	}

	/**
	 * Reads an index that {@link #writeIndex} wrote.
	 */
	static @NotNull JavaItemData readIndex(@NotNull InputStream input) throws IOException {
		try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
			TreeSet<Integer> protocols = new TreeSet<>();
			for (int count = data.readInt(); protocols.size() < count; )
				protocols.add(data.readInt());
			Map<Integer, String> missing = new TreeMap<>();
			for (int count = data.readInt(); missing.size() < count; )
				missing.put(data.readInt(), data.readUTF());

			return new JavaItemData(protocols, missing);
		}
	}
}
