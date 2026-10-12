package me.whereareiam.anvil.integration.intellij;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the parts of the scenario API that editor features read, shaped as Lombok generates them, and the one JDK
 * class their constants need.
 */
@UtilityClass
public class ScenarioApiFixture {
	public void add(@NotNull CodeInsightTestFixture fixture) {
		// Light tests have no JDK; constants need String to evaluate.
		fixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}");
		fixture.addFileToProject("me/whereareiam/anvil/api/model/process/Distribution.java", """
				package me.whereareiam.anvil.api.model.process;
				public final class Distribution {
					public static Distribution remote(String version, String build) { return null; }
					public static Distribution pinned(String version, String sha256) { return null; }
				}
				""");
		fixture.addFileToProject("me/whereareiam/anvil/api/model/process/MinecraftServer.java", """
				package me.whereareiam.anvil.api.model.process;
				public class MinecraftServer {
					public static MinecraftServerBuilder builder() { return null; }
					public static class MinecraftServerBuilder {
						public MinecraftServerBuilder name(String name) { return this; }
						public MinecraftServerBuilder platform(String platform) { return this; }
						public MinecraftServerBuilder distribution(Distribution distribution) { return this; }
						public MinecraftServer build() { return null; }
					}
				}
				""");
		fixture.addFileToProject("me/whereareiam/anvil/api/model/process/MinecraftProxy.java", """
				package me.whereareiam.anvil.api.model.process;
				public class MinecraftProxy {
					public static MinecraftProxyBuilder builder() { return null; }
					public static class MinecraftProxyBuilder {
						public MinecraftProxyBuilder name(String name) { return this; }
						public MinecraftProxyBuilder platform(String platform) { return this; }
						public MinecraftProxyBuilder distribution(Distribution distribution) { return this; }
						public MinecraftProxyBuilder server(String server) { return this; }
						public MinecraftProxyBuilder defaultServer(String server) { return this; }
						public MinecraftProxy build() { return null; }
					}
				}
				""");
		fixture.addFileToProject("me/whereareiam/anvil/api/model/scenario/AnvilScenario.java", """
				package me.whereareiam.anvil.api.model.scenario;
				import me.whereareiam.anvil.api.model.process.*;
				public class AnvilScenario {
					public static AnvilScenarioBuilder builder() { return null; }
					public static class AnvilScenarioBuilder {
						public AnvilScenarioBuilder name(String name) { return this; }
						public AnvilScenarioBuilder entrypoint(String entrypoint) { return this; }
						public AnvilScenarioBuilder server(MinecraftServer server) { return this; }
						public AnvilScenarioBuilder proxy(MinecraftProxy proxy) { return this; }
						public AnvilScenarioBuilder manual(boolean manual) { return this; }
						public AnvilScenario build() { return null; }
					}
				}
				""");
	}
}
