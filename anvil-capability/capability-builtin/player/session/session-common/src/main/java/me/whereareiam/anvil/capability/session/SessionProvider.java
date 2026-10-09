package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider of the standard session capability over the player's typed channel, for the libraries
 * whose workers the built-in capabilities serve. Composition creates it only for players whose worker
 * installed {@link SessionBinding}; another library, such as one without a native worker, provides its own
 * session capability.
 */
public final class SessionProvider implements ProtocolPlayerCapabilityProvider<Session> {
	static final String ID = "me.whereareiam.anvil.session";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id(ID).build();
	}

	@Override
	public @NotNull Set<String> supportedLibraries() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Session> capability() {
		return Session.class;
	}

	@Override
	public @NotNull Session create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelSession(context.channel());
	}
}
