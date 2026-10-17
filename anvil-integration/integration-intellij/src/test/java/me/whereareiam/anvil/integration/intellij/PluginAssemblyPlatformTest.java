package me.whereareiam.anvil.integration.intellij;

import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableWithId;
import com.intellij.openapi.options.ex.ConfigurableGroupEP;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.account.AccountStaging;
import me.whereareiam.anvil.integration.intellij.account.settings.AccountSettingsPage;
import me.whereareiam.anvil.integration.intellij.account.storage.ProjectAccountStaging;
import me.whereareiam.anvil.integration.intellij.scenario.editor.ScenarioLineMarker;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.AnvilConfigurationType;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.AnvilDebugRunner;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.AnvilRunConfiguration;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.ScenarioConfigurationProducer;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettingsPage;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ManifestToolingCommands;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ToolingCommands;

/**
 * Checks that the plugin descriptor composes every module's fragment into one working plugin.
 */
public class PluginAssemblyPlatformTest extends BasePlatformTestCase {
	public void testRunConfigurationsRunAndDebugThroughTheAnvilRunner() {
		var type = ConfigurationTypeUtil.findConfigurationType("AnvilScenario");
		assertInstanceOf(type, AnvilConfigurationType.class);
		var configuration = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
		assertInstanceOf(configuration, AnvilRunConfiguration.class);

		var runner = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration);
		assertNotNull(runner);
		assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, configuration));
		assertInstanceOf(ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configuration), AnvilDebugRunner.class);
	}

	public void testSharedContractsAreBoundToTheirStandardImplementations() {
		assertInstanceOf(getProject().getService(BuildIntegrations.class), ProjectBuildIntegrations.class);
		assertInstanceOf(ApplicationManager.getApplication().getService(ToolingCommands.class), ManifestToolingCommands.class);
		assertInstanceOf(getProject().getService(AccountStaging.class), ProjectAccountStaging.class);
		assertTrue(ProjectBuildIntegrations.BUILD_INTEGRATIONS.getExtensionList().stream()
				.anyMatch(integration -> integration.getId().equals("gradle")));
	}

	public void testFeatureSettingsAreNestedUnderToolsAnvil() {
		var group = ConfigurableGroupEP.find("me.whereareiam.anvil");
		assertNotNull(group);
		assertEquals("tools", group.parentId);
		assertEquals("Anvil", group.getDisplayName());

		var configurables = ShowSettingsUtilImpl.getConfigurables(getProject(), true, false);
		assertEquals("me.whereareiam.anvil", parent(configurables, ScenarioSettingsPage.ID));
		assertEquals("me.whereareiam.anvil", parent(configurables, AccountSettingsPage.ID));
	}

	public void testAccountsJoinTheCatalogToolbarWithoutTheScenarioModuleKnowingThem() {
		var actions = ActionManager.getInstance();
		var toolbar = (DefaultActionGroup) actions.getAction("Anvil.Catalog.Toolbar");

		List<String> ids = Arrays.stream(toolbar.getChildActionsOrStubs()).map(actions::getId).toList();

		assertTrue(ids.toString(), ids.containsAll(List.of("Anvil.RefreshScenarios", "Anvil.Run", "Anvil.Stop", "Anvil.ManageAccounts")));
		assertNotNull(actions.getAction("Anvil.Catalog.Popup"));
		assertNotNull(actions.getAction("Anvil.Environment.Toolbar"));
	}

	public void testEditorOffersRunningScenarioDefinitions() {
		assertTrue(RunLineMarkerContributor.EXTENSION.allForLanguage(JavaLanguage.INSTANCE).stream()
				.anyMatch(contributor -> contributor instanceof ScenarioLineMarker));
		assertTrue(RunConfigurationProducer.getProducers(getProject()).stream()
				.anyMatch(producer -> producer instanceof ScenarioConfigurationProducer));
		assertNotNull(RunConfigurationProducerService.getInstance(getProject()));
	}

	public void testToolWindowIsRegistered() {
		assertTrue(ToolWindowEP.EP_NAME.getExtensionList().stream().anyMatch(window -> "Anvil".equals(window.id)));
	}

	private static String parent(List<?> configurables, String id) {
		var found = configurables.stream()
				.filter(candidate -> candidate instanceof ConfigurableWithId named && id.equals(named.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing settings page " + id));
		return ((ConfigurableWrapper) found).getParentId();
	}
}
