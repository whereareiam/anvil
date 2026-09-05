package external.fixture.capability;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Deliberately declares an unavailable predecessor to exercise discovery diagnostics.
 */
public final class BrokenCapabilityProvider implements PlayerCapabilityProvider<BrokenCapabilityProvider.Broken> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("external.fixture.broken").supportedProtocolId("fixture-broken")
				.requiredCapability(Missing.class).build();
	}

	@Override
	public @NotNull Class<Broken> capability() {
		return Broken.class;
	}

	@Override
	public @NotNull Broken create(@NotNull PlayerCapabilityContext context) {
		throw new AssertionError("Invalid graphs must fail before capability creation");
	}

	public interface Broken extends PlayerCapability { }
	public interface Missing extends PlayerCapability { }
}
