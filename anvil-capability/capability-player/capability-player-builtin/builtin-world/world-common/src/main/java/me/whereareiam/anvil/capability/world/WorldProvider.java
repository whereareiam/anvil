package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.data.JavaBlockData;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the world a player's client knows, over the player's typed channel, for the libraries the
 * world family has a worker side for. It names block states through the block data Anvil ships for the player's
 * client version. Composition creates it only for players whose worker installed the world binding.
 */
public final class WorldProvider implements ProtocolPlayerCapabilityProvider<World> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(WorldChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<World> capability() {
		return World.class;
	}

	@Override
	public @NotNull World create(@NotNull ProtocolPlayerCapabilityContext context) {
		MinecraftVersion version = MinecraftVersion.parse(context.clientVersion());
		BlockNames names = new BlockNames(JavaBlockData.shipped().find(version).orElse(null));

		return new ChannelWorld(context.channel(), names);
	}
}
