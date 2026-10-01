package me.whereareiam.anvil.integration.intellij;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableWithId;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.authentication.AccountAuthenticator;
import me.whereareiam.anvil.integration.intellij.account.authentication.BuildAccountAuthenticator;
import me.whereareiam.anvil.integration.intellij.account.persistence.ConfiguredAccountLibrary;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.runconfiguration.AnvilConfigurationType;
import me.whereareiam.anvil.integration.intellij.runconfiguration.AnvilRunConfiguration;
import me.whereareiam.anvil.integration.intellij.runconfiguration.RunConfigurationService;
import me.whereareiam.anvil.integration.intellij.scenario.ProjectScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.execution.ProjectEnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.settings.ProjectCommandHistory;
import me.whereareiam.anvil.integration.intellij.settings.StoredCommandHistory;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectSourceDiscovery;
import me.whereareiam.anvil.integration.intellij.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.tooling.ProjectToolingHost;
import me.whereareiam.anvil.integration.intellij.view.settings.AnvilSettingsConfigurable;

public class PluginAssemblyPlatformTest extends BasePlatformTestCase {
	public void testRegisteredConfigurationUsesNativeRunExecutorWithoutAdvertisingDebugger() {
		var type = ConfigurationTypeUtil.findConfigurationType("AnvilScenario");
		assertInstanceOf(type, AnvilConfigurationType.class);
		assertEquals(1, type.getConfigurationFactories().length);
		var configuration =
				type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
		assertInstanceOf(configuration, AnvilRunConfiguration.class);

		var runner = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration);
		assertNotNull("The installed plugin must have an executable Run action", runner);
		assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, configuration));
		assertNull(
				"Anvil does not implement a debugger",
				ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configuration));
	}

	public void testDescriptorBindsEngineServicesThroughTheirContracts() {
		var application = ApplicationManager.getApplication();
		assertInstanceOf(application.getService(Preferences.class), PersistentPreferences.class);
		assertInstanceOf(
				getProject().getService(BuildIntegrations.class),
				ProjectBuildIntegrations.class);
		assertInstanceOf(getProject().getService(ProjectToolingHost.class), ProjectToolingHost.class);
		assertInstanceOf(getProject().getService(ScenarioCatalog.class), ProjectScenarioCatalog.class);
		assertInstanceOf(getProject().getService(SourceDiscovery.class), ProjectSourceDiscovery.class);
		assertInstanceOf(
				getProject().getService(EnvironmentLifecycle.class), ProjectEnvironmentLifecycle.class);
		assertInstanceOf(getProject().getService(AccountLibrary.class), ConfiguredAccountLibrary.class);
		assertInstanceOf(getProject().getService(AccountAuthenticator.class), BuildAccountAuthenticator.class);
		assertInstanceOf(getProject().getService(ProjectCommandHistory.class), StoredCommandHistory.class);
		assertSame(application.getService(Preferences.class), PersistentPreferences.getInstance());
		assertSame(
				getProject().getService(AccountLibrary.class),
				ConfiguredAccountLibrary.getInstance(getProject()));
	}

	public void testDescriptorRegistersProjectRunConfigurationService() {
		var service = getProject().getService(RunConfigurationService.class);
		assertNotNull(service);
		assertSame(service, getProject().getService(RunConfigurationService.class));
	}

	public void testDescriptorBindsSettingsAndOptionalGradleIntegration() {
		assertTrue(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS.getExtensionList().stream()
						.anyMatch(integration -> integration.getId().equals("gradle")));
		var configurable = ShowSettingsUtilImpl.getConfigurables(getProject(), true, false).stream()
				.filter(
						candidate ->
								candidate instanceof ConfigurableWithId named
										&& AnvilSettingsConfigurable.ID.equals(named.getId()))
				.findFirst().orElseThrow();
		assertEquals("tools", ((ConfigurableWrapper) configurable).getParentId());
		assertNotNull(ConfigurableWrapper.cast(AnvilSettingsConfigurable.class, configurable));
	}
}
