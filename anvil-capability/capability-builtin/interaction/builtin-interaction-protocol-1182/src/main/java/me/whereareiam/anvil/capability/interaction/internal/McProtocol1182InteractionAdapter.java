package me.whereareiam.anvil.capability.interaction.internal;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Installs item, block, and entity interaction packets into an MCProtocol worker.
 */
public final class McProtocol1182InteractionAdapter implements ProtocolCapabilityAdapter {
	@Override
	public @NotNull String id() {
		return "me.whereareiam.anvil.interaction";
	}

	@Override
	public boolean supports(int protocolNumber) {
		return McProtocol1182InteractionAdapterProvider.supportsProtocol(protocolNumber);
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("interaction.item", (player, arguments) -> {
			Hand hand = hand(arguments.path("hand").asText());
			int sequence = player.nextSequence();
			player.send(new ServerboundSwingPacket(hand));
			player.send(new ServerboundUseItemPacket(hand));
			return player.mapper().createObjectNode();
		});
		registry.operation("interaction.block", (player, arguments) -> {
			Hand hand = hand(arguments.path("hand").asText());
			Direction direction = Direction.valueOf(arguments.path("face").asText());
			Position position = new Position(
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
					false
			));
			player.send(new ServerboundSwingPacket(hand));
			return player.mapper().createObjectNode();
		});
		registry.operation("interaction.entity", (player, arguments) -> {
			Hand hand = hand(arguments.path("hand").asText());
			int entityId = arguments.path("entityId").asInt();
			InteractAction action = InteractAction.valueOf(arguments.path("interaction").asText());
			// Hand use includes a hit-position interaction before the generic fallback on this protocol.
			if (action == InteractAction.INTERACT)
				player.send(new ServerboundInteractPacket(entityId, InteractAction.INTERACT_AT, hand, false));
			player.send(new ServerboundInteractPacket(entityId, action, hand, false));
			player.send(new ServerboundSwingPacket(hand));
			return player.mapper().createObjectNode();
		});
	}

	private static Hand hand(String value) {
		return value.equals("OFF") ? Hand.OFF_HAND : Hand.MAIN_HAND;
	}

}
