package me.whereareiam.anvil.integration.intellij;

import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.scenario.console.CommandHistory;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLogListener;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.RunConfigurationService;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ToolingConnection;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ToolingHost;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import org.jetbrains.annotations.NotNull;

/**
 * Gives each test fresh scenario services over the module's real descriptor, which the test plugin loads.
 * <p>
 * Services that keep state between tests are replaced by new instances, so a test starts from defaults and its
 * launches end with it. {@link #tooling} replaces the process starter, for tests that control the runner.
 */
public abstract class ScenarioPlatformTestCase extends BasePlatformTestCase {
	private final Map<SessionLog, RecordingLog> recordings = new WeakHashMap<>();

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// Actions read the selection from the component hierarchy, as they do in the IDE.
		HeadlessDataManager.fallbackToProductionDataManager(getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				ApplicationManager.getApplication(), ScenarioSettings.class, new ScenarioSettings(), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), BuildIntegrations.class, new ProjectBuildIntegrations(getProject()), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), RunConfigurationService.class, new RunConfigurationService(getProject()), getTestRootDisposable());
		var history = new CommandHistory();
		ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), CommandHistory.class, history, getTestRootDisposable());
		Disposer.register(getTestRootDisposable(), history);
		tooling(ProcessBuilder::start);
		RunManagerImpl.getInstanceImpl(getProject()).clearAll();
	}

	/**
	 * Replaces the tooling host and every service that observes it, so launches use the given process starter.
	 */
	protected EnvironmentLifecycle tooling(ToolingConnection.Starter starter) {
		var host = new ToolingHost(getProject(), starter);
		ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), ToolingHost.class, host, getTestRootDisposable());
		var catalog = new ScenarioCatalog(getProject());
		ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), ScenarioCatalog.class, catalog, getTestRootDisposable());
		var environments = new EnvironmentLifecycle(getProject());
		ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), EnvironmentLifecycle.class, environments, getTestRootDisposable());
		var discovery = new SourceDiscovery(getProject());
		ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), SourceDiscovery.class, discovery, getTestRootDisposable());
		Disposer.register(getTestRootDisposable(), discovery);
		Disposer.register(getTestRootDisposable(), host);
		Disposer.register(getTestRootDisposable(), catalog);
		Disposer.register(getTestRootDisposable(), environments);
		return environments;
	}

	protected RecordingLog recorded(SessionLog log) {
		return recordings.computeIfAbsent(log, ignored -> new RecordingLog(log, getTestRootDisposable()));
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
