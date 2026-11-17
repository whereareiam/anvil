package me.whereareiam.anvil.protocol.java.packet;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.type.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.model.PacketDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The shipped packet data's binary form, written by the build's generator and read by {@link JavaPacketData}: one
 * class for both directions, so the two can never disagree. Both ship in this module's JAR, so the data carries no
 * format version. States are written by name and checked by {@code valueOf} when read.
 *
 * <p>A registry is its protocol, its {@link PacketDataSource}, and for each connection state the names of the packets
 * the server sends, by ID. The index lists each supported protocol, with the reason when it has no data.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PacketDataCodec {
	/**
	 * Writes a registry, compressed.
	 */
	static void writeRegistry(@NotNull PacketRegistry registry, @NotNull OutputStream output) throws IOException {
		try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(output))) {
			data.writeInt(registry.protocol());
			PacketDataSource source = registry.source();
			data.writeUTF(source.getRepository());
			data.writeUTF(source.getVersion());
			data.writeUTF(source.getCommit());
			data.writeUTF(source.getProtocol());

			data.writeInt(ConnectionState.values().length);
			for (ConnectionState state : ConnectionState.values()) {
				List<String> names = registry.clientbound(state);
				data.writeUTF(state.name());
				data.writeInt(names.size());
				for (String name : names)
					data.writeUTF(name);
			}
		}
	}

	/**
	 * Reads a registry that {@link #writeRegistry} wrote.
	 */
	static @NotNull PacketRegistry readRegistry(@NotNull InputStream input) throws IOException {
		try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
			int protocol = data.readInt();
			PacketDataSource source = PacketDataSource.builder()
					.repository(data.readUTF())
					.version(data.readUTF())
					.commit(data.readUTF())
					.protocol(data.readUTF())
					.build();

			Map<ConnectionState, List<String>> clientbound = new EnumMap<>(ConnectionState.class);
			for (int states = data.readInt(); clientbound.size() < states; ) {
				ConnectionState state = ConnectionState.valueOf(data.readUTF());
				List<String> names = new ArrayList<>();
				for (int count = data.readInt(); names.size() < count; )
					names.add(data.readUTF());
				clientbound.put(state, names);
			}

			return new PacketRegistry(protocol, source, clientbound);
		}
	}

	/**
	 * Writes the index: each supported protocol, and the reason for each that has no data.
	 */
	static void writeIndex(@NotNull JavaPacketData index, @NotNull OutputStream output) throws IOException {
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
	static @NotNull JavaPacketData readIndex(@NotNull InputStream input) throws IOException {
		try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
			TreeSet<Integer> protocols = new TreeSet<>();
			for (int count = data.readInt(); protocols.size() < count; )
				protocols.add(data.readInt());
			Map<Integer, String> missing = new TreeMap<>();
			for (int count = data.readInt(); missing.size() < count; )
				missing.put(data.readInt(), data.readUTF());

			return new JavaPacketData(protocols, missing);
		}
	}
}
