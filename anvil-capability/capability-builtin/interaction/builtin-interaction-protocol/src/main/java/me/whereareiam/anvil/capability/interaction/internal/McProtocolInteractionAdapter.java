package me.whereareiam.anvil.capability.interaction.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Installs item, block, and entity interaction packets into an MCProtocol worker.
 */
public final class McProtocolInteractionAdapter implements ProtocolCapabilityAdapter {
	@Override
	public @NotNull String id() {
		return McProtocolInteractionProvider.ID;
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("interaction.item", (player, arguments) -> {
			Hand hand = hand(arguments.path("hand").asText());
			int sequence = player.nextSequence();
			player.send(new ServerboundSwingPacket(hand));
			player.send(new ServerboundUseItemPacket(hand, sequence, player.yaw(), player.pitch()));
			return player.mapper().createObjectNode();
		});
		registry.operation("interaction.block", (player, arguments) -> {
			Hand hand = hand(arguments.path("hand").asText());
			Direction direction = Direction.valueOf(arguments.path("face").asText());
			Vector3i position = Vector3i.from(
					arguments.path("x").asInt(),
					arguments.path("y").asInt(),
					arguments.path("z").asInt()
			);
			player.send(new ServerboundUseItemOnPacket(
					position,
					direction,
					hand,
					0.5F,
					0.5F,
					0.5F,
					false,
					false,
					player.nextSequence()
			));
			player.send(new ServerboundSwingPacket(hand));
			return player.mapper().createObjectNode();
		});
		registry.operation("interaction.entity", (player, arguments) -> {
			Hand hand = hand(arguments.path("hand").asText());
			player.send(entityPacket(
					arguments.path("entityId").asInt(),
					arguments.path("interaction").asText(),
					hand
			));
			player.send(new ServerboundSwingPacket(hand));
			return player.mapper().createObjectNode();
		});
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
