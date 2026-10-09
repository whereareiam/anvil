package external.fixture.protocol;

import org.jetbrains.annotations.NotNull;

/**
 * Exposes fixture SDK probing without Session or movement adapters, alongside agent capabilities.
 */
public final class ObservationLibraryProvider extends FixtureLibraryProvider {
	@Override
	public @NotNull String id() {
		return "fixture-observer";
	}
}
