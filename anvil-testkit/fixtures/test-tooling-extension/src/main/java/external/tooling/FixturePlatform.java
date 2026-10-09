package external.tooling;

import java.net.URL;
import java.util.regex.Pattern;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the test-process artifact through the normal execution and readiness lifecycle.
 * Its Java data lives in {@code fixture-tooling-versions.toml}.
 */
public final class FixturePlatform implements PlatformProvider {
	private static final URL VERSION_DATA = FixturePlatform.class.getResource("fixture-tooling-versions.toml");

	@Override public @NotNull String id() { return "fixture-tooling"; }
	@Override public @NotNull Class<? extends MinecraftProcess> configurationType() { return MinecraftServer.class; }
	@Override public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
		return ResolvedDistribution.builder().jar(process.getDistribution().getLocalJar()).description("Gradle-built process fixture").build();
	}
	@Override public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) { }
	@Override public @NotNull Pattern readinessPattern() { return Pattern.compile("READY"); }
	@Override public @NotNull URL versionData() { return VERSION_DATA; }
}
