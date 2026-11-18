package me.whereareiam.anvil.capability.packets.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a comparison of two recorded packet streams leaves out, because it differs from run to run rather than between
 * the builds compared. By default nothing is left out: frames pair up in order, and a pair is equal when its state,
 * name and bytes are.
 *
 * <ul>
 *     <li>Ignoring a packet name, such as {@code keep_alive}, leaves every frame of that name out of both streams
 *     before they pair up, so neither their presence nor their order matters.</li>
 *     <li>Ignoring fields of a packet class compares the frames whose value is of that class by value, without those
 *     fields, instead of by bytes. Ignoring a class without fields compares it by value alone, which leaves out what
 *     the protocol does not model, such as a login's hashed seed. A {@linkplain ClientboundPackets#isCostly costly}
 *     class, such as a chunk column, cannot be compared by value, because recordings keep its bytes alone; leave it
 *     out by name instead, such as {@code map_chunk}.</li>
 * </ul>
 *
 * <p>Name fields through the constants Lombok generates on every packet class, so the compiler checks them; a
 * comparison refuses a field the class does not have.</p>
 *
 * <pre>{@code
 * PacketMask mask = PacketMask.runVarying()
 *         .ignore("bundle_delimiter")
 *         .ignore(ClientboundSystemChat.class, ClientboundSystemChat.Fields.text);
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PacketMask {
	/**
	 * The minecraft-data names of the packets left out of both streams.
	 */
	@NotNull Set<String> names;
	/**
	 * The packet classes compared by value, each with the fields left out of the comparison.
	 */
	@NotNull Map<Class<? extends ClientboundPacket>, Set<String>> fields;

	/**
	 * Leaves nothing out.
	 *
	 * @return the strict mask
	 */
	public static @NotNull PacketMask strict() {
		return new PacketMask(Set.of(), Map.of());
	}

	/**
	 * Leaves out what Anvil knows to differ between two runs of one scenario on one build: keep-alives, pings and
	 * the periodic time updates, whose presence follows the clock, the hashed seed of a login or a respawn, and the
	 * signatures and salts of chat.
	 *
	 * @return the mask
	 */
	public static @NotNull PacketMask runVarying() {
		return strict()
				.ignore("keep_alive")
				.ignore("ping")
				.ignore("update_time")
				.ignore(ClientboundLogin.class)
				.ignore(ClientboundRespawn.class)
				.ignore(ClientboundPlayerChat.class, ClientboundPlayerChat.Fields.signature,
						ClientboundPlayerChat.Fields.lastSeen)
				.ignore(ClientboundDeleteChat.class, ClientboundDeleteChat.Fields.signature);
	}

	/**
	 * Leaves the packets of a minecraft-data name out of both streams.
	 *
	 * @param name the name, such as {@code keep_alive}
	 * @return a mask that also leaves them out
	 * @throws IllegalArgumentException when the name is blank
	 */
	public @NotNull PacketMask ignore(@NotNull String name) {
		if (name.isBlank()) throw new IllegalArgumentException("A packet name cannot be blank");

		Set<String> joined = new HashSet<>(names);
		joined.add(name);
		return new PacketMask(Set.copyOf(joined), fields);
	}

	/**
	 * Compares the packets of a class by value, without some fields.
	 *
	 * @param type the packet class
	 * @param ignored the fields to leave out, through the class's {@code Fields} constants; none compares the class
	 * by value alone
	 * @return a mask that also compares them so
	 * @throws IllegalArgumentException when the class is costly, so recordings keep no value of it
	 */
	public @NotNull PacketMask ignore(@NotNull Class<? extends ClientboundPacket> type, @NotNull String @NotNull ... ignored) {
		if (ClientboundPackets.isCostly(type))
			throw new IllegalArgumentException(type.getSimpleName() + " is costly to decode, so recordings keep its bytes "
					+ "alone and compare them; leave its packets out by name instead");

		Map<Class<? extends ClientboundPacket>, Set<String>> joined = new HashMap<>(fields);
		Set<String> union = new HashSet<>(joined.getOrDefault(type, Set.of()));
		union.addAll(List.of(ignored));
		joined.put(type, Set.copyOf(union));

		return new PacketMask(names, Map.copyOf(joined));
	}
}
