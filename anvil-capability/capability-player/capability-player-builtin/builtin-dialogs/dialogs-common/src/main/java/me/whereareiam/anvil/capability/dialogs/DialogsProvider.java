package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the dialogs a player is shown and the buttons it presses, over the player's typed channel,
 * for the libraries the dialogs family has a worker side for. Composition creates it only for players whose worker
 * installed the dialogs extension, which a worker of a release without dialogs does not. Buttons that run commands are
 * checked through the player's {@link Commands} when it has them; without them, every other button still works.
 */
public final class DialogsProvider implements ProtocolPlayerCapabilityProvider<Dialogs> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(DialogsChannel.ID)
				.optionalCapability(Commands.class)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedLibraries() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Dialogs> capability() {
		return Dialogs.class;
	}

	@Override
	public @NotNull Dialogs create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelDialogs(context.channel(), context.findCapability(Commands.class).orElse(null));
	}
}
