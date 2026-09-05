package external.fixture.protocol;

import org.jetbrains.annotations.NotNull;

/**
 * Selects only agent capabilities; no Session or packet-capability adapter targets this ID.
 */
public final class ObservationProtocolProvider extends FixtureProtocolProvider {
	@Override
	public @NotNull String id() {
		return "fixture-observer";
	}
}
