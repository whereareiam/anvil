package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;


/**
 * Host-side provider of the standard session capability over the player's typed channel. Its worker side,
 * {@link SessionBinding}, needs no edition protocol, so composition creates it for the players of every library
 * whose worker installed it; a library without a native worker provides its own session capability.
 */
public final class SessionProvider implements ProtocolPlayerCapabilityProvider<Session> {
	static final String ID = "me.whereareiam.anvil.session";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id(ID).build();
	}

	@Override
	public boolean workerBacked() {
		return true;
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
