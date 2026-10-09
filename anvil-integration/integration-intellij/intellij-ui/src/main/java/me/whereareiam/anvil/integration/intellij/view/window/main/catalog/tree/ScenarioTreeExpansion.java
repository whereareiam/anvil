package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree;

import com.intellij.ui.treeStructure.Tree;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.jetbrains.annotations.NotNull;

/**
 * Retains user expansion choices independently from catalog refreshes and search filtering.
 */
public final class ScenarioTreeExpansion {
	private final Map<String, Boolean> choices = new HashMap<>();
	private boolean automatic;
	private boolean restoring;

	boolean setAutomatic(boolean automatic) {
		if (this.automatic == automatic) return false;
		this.automatic = automatic;
		choices.clear();
		return true;
	}

	void remember(
			@NotNull TreeExpansionEvent event,
			boolean expanded,
			@NotNull Function<DefaultMutableTreeNode, String> identity
	) {
		if (restoring) return;
		if (event.getPath().getLastPathComponent() instanceof DefaultMutableTreeNode node) {
			String key = identity.apply(node);
			if (key != null) choices.put(key, expanded);
		}
	}

	void retain(@NotNull Set<String> identities) {
		choices.keySet().retainAll(identities);
	}

	void restore(
			@NotNull Tree tree,
			@NotNull DefaultMutableTreeNode root,
			@NotNull Function<DefaultMutableTreeNode, String> identity
	) {
		restoring = true;

		try {
			for (int index = 0; index < root.getChildCount(); index++) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(index);
				TreePath path = new TreePath(node.getPath());
				if (choices.getOrDefault(identity.apply(node), automatic)) {
					tree.expandPath(path);
					continue;
				}
				tree.collapsePath(path);
			}
		} finally {
			restoring = false;
		}
	}
}
