package me.whereareiam.anvil.integration.intellij.view.window.main;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

import org.jetbrains.annotations.NotNull;

/**
 * Creates the Anvil scenario browser and live session controls on demand.
 */
public final class AnvilToolWindowFactory implements ToolWindowFactory, DumbAware {
	/**
	 * Tool-window identifier registered by the plugin descriptor.
	 */
	public static final @NotNull String ID = "Anvil";

	@Override
	public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
		new MainWindowController(project, toolWindow.getContentManager());
	}
}
