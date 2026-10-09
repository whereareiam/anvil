package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree;

import java.util.List;
import java.util.Locale;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Projects scenario descriptors into typed tree nodes and restores stable selections.
 */
public final class ScenarioTreeModel {
	private final @NotNull DefaultMutableTreeNode root = new DefaultMutableTreeNode("Scenarios");
	private final @NotNull DefaultTreeModel model = new DefaultTreeModel(root);

	@NotNull DefaultTreeModel model() {
		return model;
	}

	@NotNull DefaultMutableTreeNode root() {
		return root;
	}

	void rebuild(@NotNull List<ScenarioDescriptor> scenarios, @NotNull String search) {
		root.removeAllChildren();
		String query = search.strip().toLowerCase(Locale.ROOT);
		for (ScenarioDescriptor scenario : scenarios) {
			if (!matches(scenario, query)) continue;

			ScenarioNode parent = new ScenarioNode(scenario);
			if (ScenarioPresentation.standaloneProcess(scenario) == null) {
				for (ProcessDefinition process : scenario.getProcesses())
					parent.add(new ProcessNode(scenario, process));
			}

			root.add(parent);
		}

		model.reload();
	}

	@Nullable TreePath pathFor(@Nullable String identity) {
		if (identity == null) return null;
		var nodes = root.depthFirstEnumeration();

		while (nodes.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
			if (identity.equals(identity(node))) return new TreePath(node.getPath());
		}

		return null;
	}

	@Nullable
	static String identity(@NotNull DefaultMutableTreeNode node) {
		Object value = node.getUserObject();
		if (value instanceof ScenarioNode scenarioNode) value = scenarioNode.getUserObject();
		if (value instanceof ProcessNode processNode) value = processNode.getUserObject();
		if (value instanceof ScenarioDescriptor scenario) return ScenarioPresentation.identity(scenario);

		if (value instanceof ProcessDefinition process
				&& node.getParent() instanceof DefaultMutableTreeNode parent) {
			String parentIdentity = identity(parent);
			return parentIdentity == null ? null : parentIdentity + "/" + process.getName();
		}

		return null;
	}

	@Nullable
	static ScenarioDescriptor scenario(@Nullable Object value) {
		if (value instanceof ScenarioNode scenarioNode)
			return (ScenarioDescriptor) scenarioNode.getUserObject();

		if (value instanceof ScenarioDescriptor scenario) return scenario;
		if (value instanceof ProcessNode process) return process.scenario();

		return null;
	}

	@Nullable
	static ProcessDefinition process(@Nullable Object value) {
		if (value instanceof ProcessNode processNode)
			return (ProcessDefinition) processNode.getUserObject();

		if (value instanceof ProcessDefinition process) return process;
		if (value instanceof ScenarioNode scenarioNode)
			value = scenarioNode.getUserObject();

		if (value instanceof ScenarioDescriptor scenario) return ScenarioPresentation.standaloneProcess(scenario);

		return null;
	}

	static boolean matches(@NotNull ScenarioDescriptor scenario, @NotNull String query) {
		if (query.isBlank()) return true;

		StringBuilder text = new StringBuilder(scenario.getDisplayName())
				.append(' ')
				.append(scenario.getName());

		if (scenario.getDescription() != null) text.append(' ').append(scenario.getDescription());
		if (scenario.getCategory() != null) text.append(' ').append(scenario.getCategory());

		text.append(' ').append(String.join(" ", scenario.getTags()));
		for (ProcessDefinition process : scenario.getProcesses()) {
			text.append(' ')
					.append(process.getDisplayName())
					.append(' ')
					.append(process.getPlatform());
		}

		return text.toString()
				.toLowerCase(Locale.ROOT)
				.contains(query);
	}

	public static final class ScenarioNode extends DefaultMutableTreeNode {
		public ScenarioNode(@NotNull ScenarioDescriptor scenario) {
			super(scenario);
		}
	}

	public static final class ProcessNode extends DefaultMutableTreeNode {
		private final @NotNull ScenarioDescriptor scenario;

		public ProcessNode(
				@NotNull ScenarioDescriptor scenario,
				@NotNull ProcessDefinition process
		) {
			super(process);
			this.scenario = scenario;
		}

		@NotNull ScenarioDescriptor scenario() {
			return scenario;
		}
	}
}
