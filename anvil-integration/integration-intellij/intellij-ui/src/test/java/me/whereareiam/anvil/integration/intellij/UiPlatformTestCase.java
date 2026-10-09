package me.whereareiam.anvil.integration.intellij;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.authentication.AccountAuthenticator;
import me.whereareiam.anvil.integration.intellij.account.authentication.BuildAccountAuthenticator;
import me.whereareiam.anvil.integration.intellij.account.persistence.ConfiguredAccountLibrary;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.runconfiguration.AnvilConfigurationType;
import me.whereareiam.anvil.integration.intellij.runconfiguration.RunConfigurationService;
import me.whereareiam.anvil.integration.intellij.scenario.ProjectScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.execution.ProjectEnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.settings.ProjectCommandHistory;
import me.whereareiam.anvil.integration.intellij.settings.StoredCommandHistory;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectSourceDiscovery;
import me.whereareiam.anvil.integration.intellij.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.tooling.ProjectToolingHost;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingConnection;

/**
 * Binds native UI collaborators locally; final descriptor wiring is tested by the assembly.
 */
public abstract class UiPlatformTestCase extends BasePlatformTestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		var area = ApplicationManager.getApplication().getExtensionArea();
		String name = ProjectBuildIntegrations.BUILD_INTEGRATIONS.getName();
		if (!area.hasExtensionPoint(name)) {
			area.registerExtensionPoint(name, BuildIntegration.class.getName(), ExtensionPoint.Kind.INTERFACE);
			Disposer.register(getTestRootDisposable(), () -> area.unregisterExtensionPoint(name));
		}
		var application = ApplicationManager.getApplication();
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				application, Preferences.class, new PersistentPreferences(), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), BuildIntegrations.class, new ProjectBuildIntegrations(getProject()), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), AccountLibrary.class, new ConfiguredAccountLibrary(getProject()), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), AccountAuthenticator.class, new BuildAccountAuthenticator(getProject()), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), RunConfigurationService.class, new RunConfigurationService(getProject()), getTestRootDisposable());
		tooling(ProcessBuilder::start);
		var history = new StoredCommandHistory();
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), ProjectCommandHistory.class, history, getTestRootDisposable());
		Disposer.register(getTestRootDisposable(), history);

		ExtensionTestUtil.addExtensions(
				ConfigurationType.CONFIGURATION_TYPE_EP,
				List.of(new AnvilConfigurationType()),
				getTestRootDisposable());
		var runs = RunManagerImpl.getInstanceImpl(getProject());
		runs.clearAll();
		runs.initializeConfigurationTypes(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList());
	}
	protected ProjectEnvironmentLifecycle tooling(ToolingConnection.Starter starter) {
		var host = new ProjectToolingHost(getProject(), starter);
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), ProjectToolingHost.class, host, getTestRootDisposable());
		var catalog = new ProjectScenarioCatalog(getProject());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), ScenarioCatalog.class, catalog, getTestRootDisposable());
		var environments = new ProjectEnvironmentLifecycle(getProject());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), EnvironmentLifecycle.class, environments, getTestRootDisposable());
		var discovery = new ProjectSourceDiscovery(getProject());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), SourceDiscovery.class, discovery, getTestRootDisposable());
		Disposer.register(getTestRootDisposable(), discovery);
		Disposer.register(getTestRootDisposable(), host);
		Disposer.register(getTestRootDisposable(), catalog);
		Disposer.register(getTestRootDisposable(), environments);
		return environments;
	}

}
