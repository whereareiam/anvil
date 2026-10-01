package me.whereareiam.anvil.integration.intellij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import org.jdom.Element;

public class PersistentPreferencesPlatformTest extends EnginePlatformTestCase {
	private PersistentPreferences settings;
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new PersistentPreferences();
		ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), Preferences.class, settings, getTestRootDisposable());
	}
	public void testPersistentComponentKeepsItsExistingStorageIdentity() {
		var state = PersistentPreferences.class.getAnnotation(com.intellij.openapi.components.State.class);
		assertEquals("AnvilSettings", state.name());
		assertEquals("anvil.xml", state.storages()[0].value());
	}

	public void testDefaultIsCollapsedAndXmlStateRoundTripDoesNotAliasMutableBeans() {
		assertFalse(settings.isExpandScenarioGroups());
		assertEquals(SessionSection.LAST_USED, settings.getDefaultSessionSection());
		assertTrue(settings.isShowConsoleOnFailure());
		assertEquals(5, settings.getCompletedSuccessfulTabs());
		assertEquals(CommandHistoryPersistence.SESSION, settings.getCommandHistoryPersistence());
		assertTrue(settings.isRefreshCatalogAfterSync());
		settings.setExpandScenarioGroups(true);
		settings.setDefaultSessionSection(SessionSection.CONSOLE);
		settings.setShowConsoleOnFailure(false);
		settings.setCompletedSuccessfulTabs(8);
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		settings.setRefreshCatalogAfterSync(false);
		var saved = settings.getState();
		var encoded = XmlSerializer.serialize(saved);
		var restored = XmlSerializer.deserialize(encoded, PersistentPreferences.PreferenceState.class);
		assertTrue(restored.isExpandScenarioGroups());
		assertEquals(saved, restored);
		settings.setExpandScenarioGroups(false);
		settings.loadState(restored);
		restored.setExpandScenarioGroups(false);
		saved.setExpandScenarioGroups(false);
		assertTrue(settings.isExpandScenarioGroups());
		settings.getState().setExpandScenarioGroups(false);
		assertTrue(settings.isExpandScenarioGroups());
		assertSame(settings, PersistentPreferences.getInstance());
	}

	public void testOnlyChangedPreferencesNotifyAndDisposedViewsUnsubscribe() {
		List<Boolean> changes = new ArrayList<>();
		var owner = Disposer.newDisposable();
		Disposer.register(getTestRootDisposable(), owner);
		settings.subscribe(
				() -> {
					assertTrue(ApplicationManager.getApplication().isDispatchThread());
					changes.add(settings.isExpandScenarioGroups());
				},
				owner);
		settings.setExpandScenarioGroups(false);
		settings.setExpandScenarioGroups(true);
		settings.setExpandScenarioGroups(true);
		settings.loadState(new PersistentPreferences.PreferenceState());
		assertEquals(List.of(true, false), changes);
		Disposer.dispose(owner);
		settings.setExpandScenarioGroups(true);
		assertEquals(List.of(true, false), changes);
	}

	public void testBackgroundStateReloadNotifiesViewsOnEventThread() throws Exception {
		List<Boolean> changes = new ArrayList<>();
		settings.subscribe(
				() -> {
					assertTrue(ApplicationManager.getApplication().isDispatchThread());
					changes.add(settings.isExpandScenarioGroups());
				},
				getTestRootDisposable());
		var restored = new PersistentPreferences.PreferenceState();
		restored.setExpandScenarioGroups(true);
		ApplicationManager.getApplication()
				.executeOnPooledThread(() -> settings.loadState(restored))
				.get(5, TimeUnit.SECONDS);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(List.of(true), changes);
	}

	public void testOldXmlRetainsNewDefaultsAndInvalidStoredSelectionsAreNormalized() {
		Element legacy =
				new Element("state")
						.addContent(
								new Element("option")
										.setAttribute("name", "expandScenarioGroups")
										.setAttribute("value", "true"));
		settings.loadState(XmlSerializer.deserialize(legacy, PersistentPreferences.PreferenceState.class));
		assertTrue(settings.isExpandScenarioGroups());
		assertEquals(SessionSection.LAST_USED, settings.getDefaultSessionSection());
		assertTrue(settings.isShowConsoleOnFailure());
		assertEquals(5, settings.getCompletedSuccessfulTabs());
		assertEquals(CommandHistoryPersistence.SESSION, settings.getCommandHistoryPersistence());
		assertTrue(settings.isRefreshCatalogAfterSync());
		var invalid = settings.getState();
		invalid.setDefaultSessionSection(null);
		invalid.setCommandHistoryPersistence(null);
		invalid.setCompletedSuccessfulTabs(-1);
		settings.loadState(invalid);
		assertEquals(SessionSection.LAST_USED, settings.getDefaultSessionSection());
		assertEquals(CommandHistoryPersistence.SESSION, settings.getCommandHistoryPersistence());
		assertEquals(5, settings.getCompletedSuccessfulTabs());
	}
}
