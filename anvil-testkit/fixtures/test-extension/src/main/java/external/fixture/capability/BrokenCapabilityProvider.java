package external.fixture.capability;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Deliberately declares an unavailable predecessor to exercise discovery diagnostics.
 */
public final class BrokenCapabilityProvider implements ProtocolPlayerCapabilityProvider<BrokenCapabilityProvider.Broken> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id("external.fixture.broken")
				.requiredCapability(Missing.class)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("fixture-broken");
	}

	@Override
	public @NotNull Class<Broken> capability() {
		return Broken.class;
	}

	@Override
	public @NotNull Broken create(@NotNull ProtocolPlayerCapabilityContext context) {
		throw new AssertionError("Invalid graphs must fail before capability creation");
	}

	public interface Broken extends PlayerCapability { }
	public interface Missing extends PlayerCapability { }
}
