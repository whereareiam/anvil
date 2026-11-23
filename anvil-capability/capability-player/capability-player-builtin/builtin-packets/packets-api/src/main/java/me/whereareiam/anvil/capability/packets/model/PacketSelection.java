package me.whereareiam.anvil.capability.packets.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which packets a {@code PacketTap} receives: every packet, the packets the Java protocol models as some classes, the
 * packets of some minecraft-data names, or several of these together. A class selects the packets whose value is of
 * that class, whatever their name in the player's release; a name selects every packet of that name, such as
 * {@code update_light}, which the protocol need not model.
 *
 * <pre>{@code
 * PacketSelection chunks = PacketSelection.of(ClientboundLevelChunkWithLight.class)
 *         .and(PacketSelection.named("update_light"));
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PacketSelection {
	/**
	 * Whether every packet is selected.
	 */
	boolean everyPacket;
	/**
	 * The packet classes whose values are selected.
	 */
	@NotNull Set<Class<? extends ClientboundPacket>> types;
	/**
	 * The minecraft-data names of the selected packets.
	 */
	@NotNull Set<String> names;

	/**
	 * Selects every packet.
	 *
	 * @return the selection
	 */
	public static @NotNull PacketSelection every() {
		return new PacketSelection(true, Set.of(), Set.of());
	}

	/**
	 * Selects the packets whose value is of one of some classes.
	 *
	 * @param types packet classes of the Java protocol
	 * @return the selection
	 * @throws IllegalArgumentException when no class is given
	 */
	@SafeVarargs
	public static @NotNull PacketSelection of(@NotNull Class<? extends ClientboundPacket> @NotNull ... types) {
		return of(List.of(types));
	}

	/**
	 * Selects the packets whose value is of one of some classes.
	 *
	 * @param types packet classes of the Java protocol
	 * @return the selection
	 * @throws IllegalArgumentException when no class is given
	 */
	public static @NotNull PacketSelection of(@NotNull Collection<Class<? extends ClientboundPacket>> types) {
		if (types.isEmpty()) throw new IllegalArgumentException("A selection needs at least one packet class");

		return new PacketSelection(false, Set.copyOf(types), Set.of());
	}

	/**
	 * Selects the packets of some minecraft-data names, such as {@code update_light}.
	 *
	 * @param names packet names; the player's worker refuses a name its protocol has no packet of
	 * @return the selection
	 * @throws IllegalArgumentException when no name is given or a name is blank
	 */
	public static @NotNull PacketSelection named(@NotNull String @NotNull ... names) {
		return named(List.of(names));
	}

	/**
	 * Selects the packets of some minecraft-data names, such as {@code update_light}.
	 *
	 * @param names packet names; the player's worker refuses a name its protocol has no packet of
	 * @return the selection
	 * @throws IllegalArgumentException when no name is given or a name is blank
	 */
	public static @NotNull PacketSelection named(@NotNull Collection<String> names) {
		if (names.isEmpty()) throw new IllegalArgumentException("A selection needs at least one packet name");
		for (String name : names)
			if (name.isBlank()) throw new IllegalArgumentException("A packet name cannot be blank");

		return new PacketSelection(false, Set.of(), Set.copyOf(names));
	}

	/**
	 * Selects what this selection or another selects.
	 *
	 * @param other the other selection
	 * @return the union of both
	 */
	public @NotNull PacketSelection and(@NotNull PacketSelection other) {
		Set<Class<? extends ClientboundPacket>> joinedTypes = new HashSet<>(types);
		joinedTypes.addAll(other.types);
		Set<String> joinedNames = new HashSet<>(names);
		joinedNames.addAll(other.names);

		return new PacketSelection(everyPacket || other.everyPacket, Set.copyOf(joinedTypes), Set.copyOf(joinedNames));
	}

	/**
	 * Tells whether a tap of this selection keeps the value of a packet it takes: every value except that of a
	 * {@linkplain ClientboundPackets#isCostly costly} packet, such as a chunk column, whose class the selection does not
	 * name, so a chunk's value reaches only a tap that selects chunks by class.
	 *
	 * @param packet the packet's value
	 * @return whether the tap keeps it
	 */
	public boolean keepsValue(@NotNull ClientboundPacket packet) {
		return !ClientboundPackets.isCostly(packet.getClass()) || types.contains(packet.getClass());
	}

	/**
	 * Tells whether the selection takes a packet.
	 *
	 * @param name the packet's minecraft-data name, or {@code null} when unknown
	 * @param packet the packet's value, or {@code null} when the protocol does not model it
	 * @return whether the packet is selected
	 */
	public boolean selects(@Nullable String name, @Nullable ClientboundPacket packet) {
		if (everyPacket) return true;
		if (packet != null && types.contains(packet.getClass())) return true;

		return name != null && names.contains(name);
	}
}
