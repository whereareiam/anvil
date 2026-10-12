package me.whereareiam.anvil.integration.intellij.scenario.environment.view.services;

import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;

import java.util.List;
import javax.swing.JComponent;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.window.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusIcon;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lists the project's environments in the IDE's Services view, each with its status, its environment tab,
 * and the same actions as the Anvil tool window.
 */
public final class EnvironmentServiceContributor implements ServiceViewContributor<EnvironmentSession> {
	@Override
	public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
		return new SimpleServiceViewDescriptor("Anvil", ScenarioPresentation.scenarioIcon());
	}

	@Override
	public @NotNull List<EnvironmentSession> getServices(@NotNull Project project) {
		return EnvironmentServices.getInstance(project).sessions();
	}

	@Override
	public @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull EnvironmentSession session) {
		EnvironmentServices.getInstance(project).observe(session);
		return new Descriptor(project, session);
	}

	@RequiredArgsConstructor
	private static final class Descriptor implements ServiceViewDescriptor {
		private final @NotNull Project project;
		private final @NotNull EnvironmentSession session;

		@Override
		public @NotNull ItemPresentation getPresentation() {
			EnvironmentSession.Status status = session.getStatus();
			String label = StatusPresentation.environmentLabel(status);
			var presentation = new PresentationData();
			presentation.setPresentableText(session.getScenario().getDisplayName());
			presentation.setLocationString(label);
			presentation.setIcon(StatusIcon.overlay(StatusPresentation.environmentTone(status), label, ScenarioPresentation.scenarioIcon()));

			return presentation;
		}

		@Override
		public @NotNull String getId() {
			return session.getId();
		}

		@Override
		public @NotNull JComponent getContentComponent() {
			return EnvironmentServices.getInstance(project).view(session);
		}

		@Override
		public @Nullable ActionGroup getToolbarActions() {
			return (ActionGroup) ActionManager.getInstance().getAction("Anvil.Environment.Toolbar");
		}

		@Override
		public @Nullable ActionGroup getPopupActions() {
			return (ActionGroup) ActionManager.getInstance().getAction("Anvil.Environment.Popup");
		}

		@Override
		public @NotNull DataProvider getDataProvider() {
			return key -> {
				if (ScenarioSource.DATA_KEY.is(key)) return session.getSource();
				if (ScenarioDataKeys.SCENARIO.is(key)) return session.getScenario();
				if (ScenarioDataKeys.SESSION.is(key)) return session;
				return null;
			};
		}
	}
}
