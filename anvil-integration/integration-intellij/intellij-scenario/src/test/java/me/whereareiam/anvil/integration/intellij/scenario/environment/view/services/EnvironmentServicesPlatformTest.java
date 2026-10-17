package me.whereareiam.anvil.integration.intellij.scenario.environment.view.services;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSessionFixture;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class EnvironmentServicesPlatformTest extends ScenarioPlatformTestCase {
	private final List<ServiceEventListener.ServiceEvent> events = new CopyOnWriteArrayList<>();
	private EnvironmentServices services;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		services = EnvironmentServices.getInstance(getProject());
		getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(ServiceEventListener.TOPIC, events::add);
	}

	public void testRefreshesAnEnvironmentAfterItChanges() {
		EnvironmentSession session = session();
		services.observe(session);
		services.observe(session);

		EnvironmentSessionFixture.update(session, session.getSnapshot().toBuilder().state(SessionState.RUNNING).build());
		UIUtil.dispatchAllInvocationEvents();

		List<ServiceEventListener.ServiceEvent> changes = events.stream()
				.filter(event -> event.type == ServiceEventListener.EventType.SERVICE_CHANGED)
				.toList();
		assertEquals(1, changes.size());
		assertSame(session, changes.getFirst().target);
		assertEquals(EnvironmentServiceContributor.class, changes.getFirst().contributorClass);
	}

	public void testStopsRefreshingAnEnvironmentThatEnded() {
		EnvironmentSession session = session();
		services.observe(session);

		Disposer.dispose(session);
		UIUtil.dispatchAllInvocationEvents();

		assertTrue(events.stream().noneMatch(event -> event.type == ServiceEventListener.EventType.SERVICE_CHANGED));
	}

	public void testReleasesTheViewWithItsEnvironment() {
		EnvironmentSession session = session();
		var view = services.view(session);

		Disposer.dispose(session);

		assertTrue(Disposer.isDisposed(view));
	}

	public void testClosingTheViewKeepsTheEnvironment() {
		EnvironmentSession session = session();
		var view = services.view(session);

		Disposer.dispose(view);

		assertFalse(EnvironmentSessionFixture.disposed(session));
		assertNotSame(view, services.view(session));
	}

	private EnvironmentSession session() {
		return EnvironmentSessionFixture.create(getProject(), ScenarioFixtures.source(), ScenarioFixtures.scenario(), getTestRootDisposable());
	}
}
