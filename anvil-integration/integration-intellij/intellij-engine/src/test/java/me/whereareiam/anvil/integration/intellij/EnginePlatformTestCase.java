package me.whereareiam.anvil.integration.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.authentication.AccountAuthenticator;
import me.whereareiam.anvil.integration.intellij.account.authentication.BuildAccountAuthenticator;
import me.whereareiam.anvil.integration.intellij.account.persistence.ConfiguredAccountLibrary;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.log.SessionLogListener;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
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
import org.jetbrains.annotations.NotNull;

/**
 * Installs engine boundary fixtures without loading UI code or the packaged plugin.
 */
public abstract class EnginePlatformTestCase extends BasePlatformTestCase {
	private final Map<SessionLog, RecordingLog> recordings = new WeakHashMap<>();

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
		tooling(ProcessBuilder::start);
		var history = new StoredCommandHistory();
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), ProjectCommandHistory.class, history, getTestRootDisposable());
		Disposer.register(getTestRootDisposable(), history);
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

	protected RecordingLog recorded(SessionLog log) {
		return recordings.computeIfAbsent(
				log,
				ignored -> new RecordingLog(log, getTestRootDisposable()));
	}

	protected static final class RecordingLog implements SessionLogListener {
		private final StringBuilder text = new StringBuilder();
		private final CompletableFuture<Integer> completed = new CompletableFuture<>();
		private int clearCount;

		private RecordingLog(SessionLog log, com.intellij.openapi.Disposable owner) {
			log.subscribe(this, owner);
			clearCount = 0;
		}

		@Override
		public synchronized void cleared() {
			clearCount++;
			text.setLength(0);
		}

		@Override
		public synchronized void appended(@NotNull SessionLogEntry entry) {
			text.append(entry.getText());
		}

		@Override
		public void finished(int exitCode) {
			completed.complete(exitCode);
		}

		public synchronized String text() {
			return text.toString();
		}

		public int getClearCount() {
			return clearCount;
		}

		public Integer getExitCode() {
			return completed.getNow(null);
		}

		public boolean waitFor(long timeoutMillis) throws Exception {
			completed.get(timeoutMillis, TimeUnit.MILLISECONDS);
			return true;
		}
	}
}
