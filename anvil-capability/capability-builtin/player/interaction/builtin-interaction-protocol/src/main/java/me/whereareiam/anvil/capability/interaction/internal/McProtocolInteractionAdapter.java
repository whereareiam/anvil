package me.whereareiam.anvil.capability.interaction.internal;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Owns native interaction packets and the per-player interaction sequence.
 */
@RequiredArgsConstructor
public final class McProtocolInteractionAdapter implements Interaction {
	private final @NotNull Supplier<ClientSession> sessions;
	private final @NotNull DoubleSupplier yaw;
	private final @NotNull DoubleSupplier pitch;
	private final AtomicInteger sequence = new AtomicInteger();

	@Override
	public void useItem(@NotNull me.whereareiam.anvil.capability.interaction.type.Hand selected) {
		Hand hand = hand(selected.name());
		sessions.get().send(new ServerboundSwingPacket(hand));
		sessions.get().send(new ServerboundUseItemPacket(
				hand,
				sequence.incrementAndGet(),
				(float) yaw.getAsDouble(),
				(float) pitch.getAsDouble())
		);
	}

	@Override
	public void block(@NotNull BlockPosition position, @NotNull BlockFace face, @NotNull me.whereareiam.anvil.capability.interaction.type.Hand selected) {
		Hand hand = hand(selected.name());
		sessions.get().send(new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				Direction.valueOf(face.name()), hand, 0.5F, 0.5F, 0.5F, false, false, sequence.incrementAndGet()));
		sessions.get().send(new ServerboundSwingPacket(hand));
	}

	@Override
	public void entity(int entityId, @NotNull EntityInteraction interaction, @NotNull me.whereareiam.anvil.capability.interaction.type.Hand selected) {
		Hand hand = hand(selected.name());
		sessions.get().send(entityPacket(entityId, interaction.name(), hand));
		sessions.get().send(new ServerboundSwingPacket(hand));
	}

	private static Hand hand(String value) {
		return value.equals("OFF") ? Hand.OFF_HAND : Hand.MAIN_HAND;
	}

	private static Packet entityPacket(int entityId, String interaction, Hand hand) {
		try {
			if (interaction.equals("ATTACK")) {
				try {
					Class<?> attack = Class.forName(
							"org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket"
					);
					return (Packet) attack.getConstructor(int.class).newInstance(entityId);
				} catch (ClassNotFoundException ignored) {
					// Compatible releases encode attacks in the shared interaction packet.
				}
			}

			Class<?> packet = Class.forName(
					"org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket"
			);
			try {
				return (Packet) packet
						.getConstructor(int.class, Hand.class, Vector3d.class, boolean.class)
						.newInstance(entityId, hand, Vector3d.ZERO, false);
			} catch (NoSuchMethodException ignored) {
				Class<?> actionType = Class.forName(
						"org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction"
				);
				@SuppressWarnings({"rawtypes", "unchecked"})
				Object action = Enum.valueOf((Class<? extends Enum>) actionType, interaction);
				return (Packet) packet
						.getConstructor(int.class, actionType, Hand.class, boolean.class)
						.newInstance(entityId, action, hand, false);
			}
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("The selected protocol binding cannot create an entity interaction",
					exception);
		}
	}
}
