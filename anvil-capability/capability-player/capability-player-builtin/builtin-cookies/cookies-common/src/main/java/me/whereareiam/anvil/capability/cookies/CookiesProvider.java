package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the player's cookies over its typed channel, for the libraries the cookies family has a
 * worker side for. Composition creates it only for players whose worker installed the cookies extension, which needs
 * a release with cookies, Minecraft 1.20.5 or later.
 */
public final class CookiesProvider implements ProtocolPlayerCapabilityProvider<Cookies> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(CookiesChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedLibraries() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Cookies> capability() {
		return Cookies.class;
	}

	@Override
	public @NotNull Cookies create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelCookies(context.channel());
	}
}
