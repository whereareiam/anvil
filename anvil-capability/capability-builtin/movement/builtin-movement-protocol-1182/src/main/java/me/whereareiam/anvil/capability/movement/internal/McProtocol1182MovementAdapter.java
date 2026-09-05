package me.whereareiam.anvil.capability.movement.internal;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Installs absolute movement packets into an MCProtocol worker.
 */
public final class McProtocol1182MovementAdapter implements ProtocolCapabilityAdapter {
	@Override
	public @NotNull String id() {
		return "me.whereareiam.anvil.movement";
	}

	@Override
	public boolean supports(int protocolNumber) {
		return McProtocol1182MovementAdapterProvider.supportsProtocol(protocolNumber);
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("movement.move", (player, arguments) -> {
			float yaw = (float) arguments.path("yaw").asDouble();
			float pitch = (float) arguments.path("pitch").asDouble();
			player.view(yaw, pitch);
			player.send(new ServerboundMovePlayerPosRotPacket(
					arguments.path("onGround").asBoolean(),
					arguments.path("x").asDouble(),
					arguments.path("y").asDouble(),
					arguments.path("z").asDouble(),
					yaw,
					pitch
			));
			return player.mapper().createObjectNode();
		});
	}
}
