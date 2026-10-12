package me.whereareiam.anvil.integration.intellij.scenario.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import org.jdom.Element;

public class ScenarioSettingsPlatformTest extends ScenarioPlatformTestCase {
	private ScenarioSettings settings;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new ScenarioSettings();
		ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ScenarioSettings.class, settings, getTestRootDisposable());
	}

	public void testStoredInItsOwnApplicationFile() {
		var state = ScenarioSettings.class.getAnnotation(State.class);
		assertEquals("AnvilScenarios", state.name());
		assertEquals("anvil-scenarios.xml", state.storages()[0].value());
	}

	public void testDefaultsAndXmlRoundTripDoNotAliasMutableState() {
		var defaults = settings.values();
		assertFalse(defaults.isExpandScenarios());
		assertEquals(ScenarioSettings.Section.LAST_USED, defaults.getOpeningSection());
		assertTrue(defaults.isRevealConsoleOnFailure());
		assertEquals(5, defaults.getCompletedTabs());
		assertEquals(ScenarioSettings.History.SESSION, defaults.getCommandHistory());
		assertTrue(defaults.isRefreshAfterSync());

		settings.update(ScenarioSettings.Values.builder()
				.expandScenarios(true)
				.openingSection(ScenarioSettings.Section.CONSOLE)
				.revealConsoleOnFailure(false)
				.completedTabs(8)
				.commandHistory(ScenarioSettings.History.PROJECT)
				.refreshAfterSync(false)
				.build());
		var saved = settings.getState();
		var restored = XmlSerializer.deserialize(XmlSerializer.serialize(saved), ScenarioSettings.Options.class);
		assertEquals(saved, restored);

		settings.update(ScenarioSettings.Values.builder().build());
		settings.loadState(restored);
		restored.setExpandScenarios(false);
		settings.getState().setExpandScenarios(false);
		assertTrue(settings.values().isExpandScenarios());
		assertEquals(ScenarioSettings.Section.CONSOLE, settings.values().getOpeningSection());
		assertSame(settings, ScenarioSettings.getInstance());
	}

	public void testOnlyChangesNotifyAndDisposedSubscribersStopListening() {
		List<Boolean> changes = new ArrayList<>();
		var owner = Disposer.newDisposable();
		Disposer.register(getTestRootDisposable(), owner);
		settings.subscribe(() -> {
			assertTrue(ApplicationManager.getApplication().isDispatchThread());
			changes.add(settings.values().isExpandScenarios());
		}, owner);

		settings.update(settings.values());
		settings.update(settings.values().toBuilder().expandScenarios(true).build());
		settings.update(settings.values().toBuilder().expandScenarios(true).build());
		settings.loadState(new ScenarioSettings.Options());
		assertEquals(List.of(true, false), changes);

		Disposer.dispose(owner);
		settings.update(settings.values().toBuilder().expandScenarios(true).build());
		assertEquals(List.of(true, false), changes);
	}

	public void testBackgroundReloadNotifiesOnTheEventThread() throws Exception {
		List<Boolean> changes = new ArrayList<>();
		settings.subscribe(() -> {
			assertTrue(ApplicationManager.getApplication().isDispatchThread());
			changes.add(settings.values().isExpandScenarios());
		}, getTestRootDisposable());
		var restored = new ScenarioSettings.Options();
		restored.setExpandScenarios(true);

		ApplicationManager.getApplication().executeOnPooledThread(() -> settings.loadState(restored)).get(5, TimeUnit.SECONDS);
		UIUtil.dispatchAllInvocationEvents();

		assertEquals(List.of(true), changes);
	}

	public void testPartialXmlKeepsDefaultsAndInvalidValuesAreNormalized() {
		Element partial = new Element("state").addContent(
				new Element("option").setAttribute("name", "expandScenarios").setAttribute("value", "true"));
		settings.loadState(XmlSerializer.deserialize(partial, ScenarioSettings.Options.class));
		assertTrue(settings.values().isExpandScenarios());
		assertEquals(ScenarioSettings.Section.LAST_USED, settings.values().getOpeningSection());
		assertTrue(settings.values().isRevealConsoleOnFailure());
		assertEquals(5, settings.values().getCompletedTabs());

		var invalid = settings.getState();
		invalid.setOpeningSection(null);
		invalid.setCommandHistory(null);
		invalid.setCompletedTabs(-1);
		settings.loadState(invalid);
		assertEquals(ScenarioSettings.Section.LAST_USED, settings.values().getOpeningSection());
		assertEquals(ScenarioSettings.History.SESSION, settings.values().getCommandHistory());
		assertEquals(5, settings.values().getCompletedTabs());
	}
}
