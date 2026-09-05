package external.fixture.protocol;

import org.jetbrains.annotations.NotNull;

/**
 * Selects an intentionally incomplete capability graph for failure diagnostics.
 */
public final class BrokenProtocolProvider extends FixtureProtocolProvider {
	@Override
	public @NotNull String id() {
		return "fixture-broken";
	}
}
