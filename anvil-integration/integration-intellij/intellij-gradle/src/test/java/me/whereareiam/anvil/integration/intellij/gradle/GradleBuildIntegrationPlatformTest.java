package me.whereareiam.anvil.integration.intellij.gradle;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;

import me.whereareiam.anvil.integration.intellij.gradle.resolver.ScenarioPreparationResolver;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class GradleBuildIntegrationPlatformTest extends BasePlatformTestCase {
	public void testPreparationUsesTheLinkedBuildsLocalDistributionAndOfflineMode() throws Exception {
		Path root = Files.createTempDirectory("anvil-linked-");
		Path home = Files.createDirectories(root.resolve("gradle-home"));
		Files.createDirectories(home.resolve("bin"));
		Files.createDirectories(home.resolve("lib"));
		Files.createFile(home.resolve("lib/gradle-core-api-8.14.jar"));
		Files.createFile(home.resolve("lib/gradle-base-services-8.14.jar"));
		GradleSettings gradle = GradleSettings.getInstance(getProject());
		GradleProjectSettings linked = new GradleProjectSettings();
		linked.setExternalProjectPath(root.toString());
		linked.setDistributionType(DistributionType.LOCAL);
		linked.setGradleHome(home.toString());
		gradle.linkProject(linked);
		gradle.setOfflineWork(true);

		try {
			var settings = new GradleBuildIntegration().settings(getProject(), root);

			assertEquals(home, settings.gradleHome());
			assertTrue(settings.offline());
			assertEquals(ScenarioPreparationResolver.Settings.DEFAULT,
					new GradleBuildIntegration().settings(getProject(), root.resolve("unlinked")));
		} finally {
			gradle.setOfflineWork(false);
			gradle.unlinkExternalProject(root.toString());
			FileUtil.delete(root.toFile());
		}
	}
}
