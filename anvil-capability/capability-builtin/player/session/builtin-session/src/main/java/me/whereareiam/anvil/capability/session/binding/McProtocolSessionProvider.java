package me.whereareiam.anvil.capability.session.binding;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.session.internal.McProtocolSession;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the standard session capability.
 */
public final class McProtocolSessionProvider implements ProtocolPlayerCapabilityProvider<Session> {
	static final String ID = "me.whereareiam.anvil.session";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id(ID).build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Session> capability() {
		return Session.class;
	}

	@Override
	public @NotNull Session create(@NotNull ProtocolPlayerCapabilityContext context) {
		CapabilityChannel connection = context.channel();
		if (!connection.installedCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolSession(new ChannelSessionConnection(context));
	}
}
