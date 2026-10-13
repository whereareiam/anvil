package me.whereareiam.anvil.integration.intellij.scenario.environment.view.overview;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.contribution.ContributionsPanel;
import me.whereareiam.anvil.integration.intellij.scenario.navigation.DefinitionNavigator;
import me.whereareiam.anvil.integration.intellij.scenario.navigation.WorkspaceNavigator;
import me.whereareiam.anvil.integration.intellij.scenario.window.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.details.DefinitionDetailsPanel;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusPresentation;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows an environment's processes and the selection's details; commands are registered actions.
 */
final class OverviewController {
	private final @NotNull EnvironmentSession session;
	private final @NotNull DefinitionNavigator sourceNavigator;
	private final @NotNull WorkspaceNavigator workspaceNavigator;
	private final @NotNull DefaultMutableTreeNode root;
	private final @NotNull DefaultTreeModel model;
	private final @NotNull Tree tree;
	private final @NotNull DefinitionDetailsPanel details;
	private final @NotNull ContributionsPanel contributions;
	private final @NotNull ActionToolbar toolbar;

	private @Nullable ScenarioDescriptor shownScenario;
	private @Nullable SessionSnapshot shownSnapshot;
	private @Nullable String renderedSelection;
	private @Nullable ScenarioDescriptor renderedScenario;
	private @Nullable SessionSnapshot renderedSnapshot;
	private boolean groupExpanded = true;
	private boolean updatingTree;

	OverviewController(
			@NotNull Project project,
			@NotNull EnvironmentSession session,
			@NotNull OverviewPanel view,
			@NotNull ActionToolbar toolbar
	) {
		this.session = session;
		sourceNavigator = new DefinitionNavigator(project);
		workspaceNavigator = new WorkspaceNavigator(project);

		root = view.root;
		model = view.model;
		tree = view.tree;
		details = view.details;
		contributions = view.contributions;

		this.toolbar = toolbar;

		tree.setCellRenderer(new ProcessTreeRenderer());
		tree.addTreeSelectionListener(event -> updateSelection());
		tree.addTreeExpansionListener(new ExpansionListener());
	}

	/**
	 * Returns the name of the selected process, or null when the scenario itself is selected.
	 */
	@Nullable String selectedProcessName() {
		ProcessItem selected = selectedProcess();
		return selected == null ? null : selected.name();
	}

	private static @NotNull SessionSnapshot snapshotForTree(@NotNull SessionSnapshot snapshot) {
		return snapshot.toBuilder().actions(List.of()).observations(List.of()).build();
	}

	private void rememberExpansion(@NotNull TreeExpansionEvent event, boolean expanded) {
		if (!updatingTree
				&& event.getPath().getLastPathComponent() == root
				&& root.getUserObject() instanceof ScenarioItem) groupExpanded = expanded;
	}

	private void restoreExpansion() {
		TreePath path = new TreePath(root.getPath());
		if (groupExpanded) {
			tree.expandPath(path);
			return;
		}

		tree.collapsePath(path);
	}

	void openDefinition(@NotNull String definition) {
		sourceNavigator.open(session.getSource(), definition, session, details::showNavigationStatus);
	}

	void openWorkspace(@NotNull String path) {
		workspaceNavigator.open(path, session, details::showNavigationStatus);
	}

	void update() {
		ScenarioDescriptor scenario = session.getScenario();
		SessionSnapshot snapshot = session.getSnapshot();
		SessionSnapshot presentationSnapshot = snapshotForTree(snapshot);
		if (!scenario.equals(shownScenario) || !presentationSnapshot.equals(shownSnapshot)) {
			shownScenario = scenario;
			shownSnapshot = presentationSnapshot;
			rebuildTree(scenario, presentationSnapshot);
		}

		updateSelection(scenario, snapshot, presentationSnapshot);
	}

	private void rebuildTree(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot
	) {
		updatingTree = true;
		try {
			ProcessItem previous = selectedProcess();
			Map<String, ProcessItem> processes = processItems(scenario, snapshot);
			root.removeAllChildren();

			if (isStandaloneProcessTree(scenario, processes)) {
				root.setUserObject(processes.values().iterator().next());
			} else {
				root.setUserObject(scenarioItem(scenario, snapshot));
				addProcessNodes(processes);
			}

			model.reload();
			restoreSelection(previous);
			restoreExpansion();
		} finally {
			updatingTree = false;
		}
	}

	private static @NotNull Map<String, ProcessItem> processItems(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot
	) {
		Map<String, ProcessItem> processes = new LinkedHashMap<>();
		for (ScenarioPresentation.ScenarioProcess process : ScenarioPresentation.processes(scenario, snapshot)) {
			processes.put(process.getName(), new ProcessItem(
					process.getName(),
					process.getDisplayName(),
					process.getDefinition(),
					process.getLive(),
					snapshot.getState()));
		}

		return processes;
	}

	private static boolean isStandaloneProcessTree(
			@NotNull ScenarioDescriptor scenario,
			@NotNull Map<String, ProcessItem> processes
	) {
		return processes.size() == 1 && ScenarioPresentation.standaloneProcess(scenario) != null;
	}

	private @NotNull ScenarioItem scenarioItem(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot
	) {
		EnvironmentSession.Status state = session.getStatus();
		return new ScenarioItem(
				scenario.getDisplayName(),
				state,
				StatusPresentation.environmentDescription(scenario, snapshot, state));
	}

	private void addProcessNodes(@NotNull Map<String, ProcessItem> processes) {
		for (ProcessItem process : processes.values())
			root.add(new DefaultMutableTreeNode(process));
	}

	private void restoreSelection(@Nullable ProcessItem previous) {
		DefaultMutableTreeNode selected = null;
		if (previous != null) {
			for (int index = 0; index < root.getChildCount(); index++) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(index);
				if (node.getUserObject() instanceof ProcessItem process
						&& previous.name().equals(process.name())) {
					selected = node;
					break;
				}
			}
		}

		DefaultMutableTreeNode target = selected == null ? root : selected;
		tree.setSelectionPath(new TreePath(target.getPath()));
	}

	private void updateSelection() {
		ScenarioDescriptor scenario = session.getScenario();
		SessionSnapshot snapshot = session.getSnapshot();
		updateSelection(scenario, snapshot, snapshotForTree(snapshot));
	}

	private void updateSelection(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot,
			@NotNull SessionSnapshot presentationSnapshot
	) {
		ProcessItem selected = selectedProcess();
		String selection = selected == null ? null : selected.name();
		if (!Objects.equals(selection, renderedSelection)
				|| !scenario.equals(renderedScenario)
				|| !presentationSnapshot.equals(renderedSnapshot)) {
			renderedSelection = selection;
			renderedScenario = scenario;
			renderedSnapshot = presentationSnapshot;
			showDetails(scenario, snapshot, presentationSnapshot, selected);
		}

		updateContributions(scenario, selected);
		toolbar.updateActionsAsync();
	}

	private void showDetails(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot,
			@NotNull SessionSnapshot presentationSnapshot,
			@Nullable ProcessItem selected
	) {
		if (selected == null) {
			details.showEnvironment(scenario, presentationSnapshot, session.getStatus());
			return;
		}
		if (selected.definition() != null) {
			details.showProcess(scenario, selected.definition(), selected.live(), snapshot.getState());
			return;
		}
		if (selected.live() != null) details.showRuntimeProcess(selected.live(), snapshot.getState());
	}

	private void updateContributions(
			@NotNull ScenarioDescriptor scenario,
			@Nullable ProcessItem selected
	) {
		ActionTarget scenarioTarget = target(ActionTargetType.SCENARIO, scenario.getName());
		if (selected == null) {
			contributions.update(scenarioTarget);
			return;
		}

		contributions.update(target(ActionTargetType.PROCESS, selected.name()), scenarioTarget);
	}

	private static @NotNull ActionTarget target(
			@NotNull ActionTargetType type,
			@NotNull String name
	) {
		return ActionTarget.builder().type(type).name(name).build();
	}

	private @Nullable ProcessItem selectedProcess() {
		Object selected = tree.getLastSelectedPathComponent();
		if (!(selected instanceof DefaultMutableTreeNode node)) return null;
		return node.getUserObject() instanceof ProcessItem process ? process : null;
	}

	private final class ExpansionListener implements TreeExpansionListener {
		@Override
		public void treeExpanded(TreeExpansionEvent event) {
			rememberExpansion(event, true);
		}

		@Override
		public void treeCollapsed(TreeExpansionEvent event) {
			rememberExpansion(event, false);
		}
	}

	record ProcessItem(
			@NotNull String name,
			@NotNull String displayName,
			@Nullable ProcessDefinition definition,
			@Nullable ProcessSnapshot live,
			@NotNull SessionState session
	) {
	}

	record ScenarioItem(
			@NotNull String name,
			@NotNull EnvironmentSession.Status status,
			@NotNull String description
	) {
	}
}
