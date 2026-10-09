package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the selected source's session subscription, tree overlays, and live inspector projection.
 */
@RequiredArgsConstructor
final class CatalogExecutionController implements Disposable {
	private final @NotNull CatalogView view;
	private final @NotNull CatalogDiscoveryController discovery;
	private final @NotNull EnvironmentLifecycle environments;
	private @Nullable EnvironmentSession execution;
	private @Nullable Disposable subscription;
	private boolean disposed;

	void update() {
		showExecution(environments.getActiveSession());
	}

	void showExecution(@Nullable EnvironmentSession session) {
		if (disposed) return;

		ScenarioSource source = discovery.selectedSource();
		EnvironmentSession next = session != null && session.isActive() && source != null
				&& session.getSource().getId().equals(source.getId()) ? session : null;

		if (execution != next) {
			releaseSubscription();
			execution = next;
			if (next != null) {
				subscription = Disposer.newDisposable("Anvil catalog execution");
				Disposer.register(this, subscription);
				next.subscribe(() -> {
					if (!disposed && execution == next) refreshExecution();
				}, subscription);
			}
		}

		refreshExecution();
	}

	private void refreshExecution() {
		if (disposed) return;
		if (execution != null && !execution.isActive()) {
			showExecution(null);
			return;
		}

		view.showExecution(
				execution == null ? null : execution.getScenario(),
				execution == null ? null : execution.getSnapshot(),
				execution == null ? null : execution.getEnvironmentState());
		selectionChanged();
	}

	void selectionChanged() {
		if (disposed) return;

		view.updateActions();
		ScenarioDescriptor selected = view.selectedScenario();
		if (selected == null) {
			view.details().empty();
			return;
		}

		ProcessDefinition process = view.selectedProcess();
		ScenarioSource source = discovery.selectedSource();
		if (execution == null
				|| !execution.isActive()
				|| source == null
				|| !ScenarioPresentation.runs(execution, source, selected)) {
			showDeclaration(selected, process);
			return;
		}

		ScenarioDescriptor scenario = execution.getScenario();
		var snapshot = execution.getSnapshot();
		if (process == null) {
			view.details().showEnvironment(scenario, snapshot, execution.getEnvironmentState());
			return;
		}

		var current = scenario.getProcesses().stream()
				.filter(candidate -> candidate.getName().equals(process.getName()))
				.findFirst()
				.orElse(null);
		if (current == null) {
			showDeclaration(selected, process);
			return;
		}

		var live = ScenarioPresentation.liveProcess(snapshot, current.getName());
		view.details().showProcess(scenario, current, live, snapshot.getState());
	}

	private void showDeclaration(@NotNull ScenarioDescriptor scenario, @Nullable ProcessDefinition process) {
		if (process == null) {
			view.details().showScenario(scenario);
			return;
		}

		view.details().showProcess(scenario, process, null, SessionState.IDLE);
	}


	private void releaseSubscription() {
		if (subscription == null) return;
		Disposer.dispose(subscription);
		subscription = null;
	}

	@Override
	public void dispose() {
		disposed = true;
		releaseSubscription();
		execution = null;
	}
}
