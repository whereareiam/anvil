package me.whereareiam.anvil.integration.intellij.scenario.environment.view.services;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.ide.projectView.PresentationData;

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.EnvironmentTab;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusPresentation;

public class EnvironmentServiceContributorPlatformTest extends ScenarioPlatformTestCase {
	private final EnvironmentServiceContributor contributor = new EnvironmentServiceContributor();

	public void testListsTheProjectsEnvironmentsUnderAnvil() {
		var root = (PresentationData) contributor.getViewDescriptor(getProject()).getPresentation();

		assertEquals("Anvil", root.getPresentableText());
		assertEmpty(contributor.getServices(getProject()));
	}

	public void testPresentsTheEnvironmentWithItsStatus() {
		EnvironmentSession session = session();

		ServiceViewDescriptor descriptor = contributor.getServiceDescriptor(getProject(), session);
		var presentation = (PresentationData) descriptor.getPresentation();

		assertEquals(session.getId(), descriptor.getId());
		assertEquals("Player registration", presentation.getPresentableText());
		assertEquals(StatusPresentation.environmentLabel(session.getStatus()), presentation.getLocationString());
		assertNotNull(presentation.getIcon(false));
	}

	public void testOffersTheEnvironmentToTheAnvilActions() {
		EnvironmentSession session = session();

		var data = contributor.getServiceDescriptor(getProject(), session).getDataProvider();

		assertSame(session, data.getData(ScenarioDataKeys.SESSION.getName()));
		assertSame(session.getScenario(), data.getData(ScenarioDataKeys.SCENARIO.getName()));
		assertSame(session.getSource(), data.getData(ScenarioSource.DATA_KEY.getName()));
		assertNull(data.getData(ScenarioDataKeys.PROCESS.getName()));
	}

	public void testUsesTheEnvironmentToolbarAndPopup() {
		ServiceViewDescriptor descriptor = contributor.getServiceDescriptor(getProject(), session());

		assertNotNull(descriptor.getToolbarActions());
		assertNotNull(descriptor.getPopupActions());
	}

	public void testShowsTheSameEnvironmentViewEachTime() {
		EnvironmentSession session = session();
		ServiceViewDescriptor descriptor = contributor.getServiceDescriptor(getProject(), session);

		var view = descriptor.getContentComponent();

		assertInstanceOf(view, EnvironmentTab.class);
		assertSame(view, contributor.getServiceDescriptor(getProject(), session).getContentComponent());
	}

	private EnvironmentSession session() {
		return EnvironmentSessionFixture.create(getProject(), ScenarioFixtures.source(), ScenarioFixtures.scenario(), getTestRootDisposable());
	}
}
