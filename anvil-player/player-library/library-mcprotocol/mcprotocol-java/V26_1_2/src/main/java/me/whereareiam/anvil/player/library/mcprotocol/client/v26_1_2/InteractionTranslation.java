package me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Interaction of the MCProtocolLib releases from 26.1 on, which send an attack in a packet of its own and every
 * interaction with the hand and the point of the entity, its position when the interaction names none. Each release's
 * {@code InteractionPackets} carries what changed after 26.1: the arm swing, which Minecraft 26.3 leaves to the
 * server, and the punch that 26.3 sends after an attack.
 */
public abstract class InteractionTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		Set<Class<? extends JavaPacket>> packets = new HashSet<>(Set.of(
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class
		));
		packets.addAll(releasePackets());

		return Set.copyOf(packets);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return null;
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return switch (packet) {
			case ServerboundUseItem use -> new ServerboundUseItemPacket(ValueTranslator.hand(use.hand()),
					use.sequence(), use.yaw(), use.pitch());
			case ServerboundUseItemOn use -> useItemOn(use);
			case ServerboundInteract interact -> interact(interact);
			case ServerboundAttack attack -> new ServerboundAttackPacket(attack.entityId().value());
			default -> encodeRelease(packet);
		};
	}

	/**
	 * Returns the interaction packets only the release carries.
	 *
	 * @return packet classes
	 */
	protected abstract @NotNull Set<Class<? extends JavaPacket>> releasePackets();

	/**
	 * Builds a packet only the release carries.
	 *
	 * @param packet the packet to send
	 * @return the release's packet, or {@code null} when the packet is not one of the release's interaction packets
	 */
	protected abstract @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet);

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.position();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.x(), position.y(), position.z()),
				ValueTranslator.direction(use.face()), ValueTranslator.hand(use.hand()), use.cursorX(),
				use.cursorY(), use.cursorZ(), use.insideBlock(), use.worldBorderHit(), use.sequence());
	}

	private static Packet interact(ServerboundInteract interact) {
		Vec3d point = interact.point() == null ? Vec3d.ZERO : interact.point();
		return new ServerboundInteractPacket(interact.entityId().value(),
				ValueTranslator.hand(interact.hand()), Vector3d.from(point.x(), point.y(), point.z()),
				interact.sneaking());
	}
}
