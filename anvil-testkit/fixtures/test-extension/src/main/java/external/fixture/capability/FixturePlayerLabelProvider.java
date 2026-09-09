package external.fixture.capability;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a player using shared identity context without protocol or agent services.
 */
public final class FixturePlayerLabelProvider implements PlayerCapabilityProvider<FixturePlayerLabelProvider.Label> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("external.fixture.player-label").build();
	}

	@Override
	public @NotNull Class<Label> capability() {
		return Label.class;
	}

	@Override
	public @NotNull Label create(@NotNull PlayerCapabilityContext context) {
		String value = context.playerName() + "@" + context.clientVersion();
		return () -> value;
	}

	/**
	 * Player identity and selected version observed by a mechanism-independent provider.
	 */
	public interface Label extends PlayerCapability {
		/**
		 * Returns the player's configured name and selected client version.
		 *
		 * @return name and version separated by an at sign
		 */
		@NotNull String value();
	}
}
