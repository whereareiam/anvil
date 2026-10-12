package me.whereareiam.anvil.integration.intellij.scenario.console;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The console toolbar: a level filter, error navigation, and the native console's own actions such as soft wraps
 * and scrolling to the end. Search uses the console's native Find (Ctrl+F).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConsoleActions {
	/**
	 * Creates the toolbar actions of one console presentation.
	 */
	public static @NotNull ActionGroup create(@NotNull ConsolePresentation presentation) {
		var levels = DefaultActionGroup.createPopupGroup(() -> "Show Levels");
		levels.getTemplatePresentation().setIcon(AllIcons.General.Filter);
		levels.getTemplatePresentation().setDescription("Choose which log levels the console shows");
		for (ConsoleLevel level : ConsoleLevel.values()) levels.add(new LevelFilter(presentation, level));

		var group = new DefaultActionGroup();
		group.add(levels);
		group.add(new ErrorNavigation(presentation, false));
		group.add(new ErrorNavigation(presentation, true));
		group.add(Separator.getInstance());
		for (AnAction action : presentation.getConsole().createConsoleActions()) group.add(action);

		return group;
	}

	private static final class LevelFilter extends DumbAwareToggleAction {
		private final @NotNull ConsolePresentation presentation;
		private final @NotNull ConsoleLevel level;

		private LevelFilter(@NotNull ConsolePresentation presentation, @NotNull ConsoleLevel level) {
			super(level.getLabel());
			this.presentation = presentation;
			this.level = level;
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}

		@Override
		public boolean isSelected(@NotNull AnActionEvent event) {
			return presentation.getLevelFilter() == level;
		}

		@Override
		public void setSelected(@NotNull AnActionEvent event, boolean state) {
			if (state) presentation.setLevelFilter(level);
		}
	}

	private static final class ErrorNavigation extends DumbAwareAction {
		private final @NotNull ConsolePresentation presentation;
		private final boolean forward;

		private ErrorNavigation(@NotNull ConsolePresentation presentation, boolean forward) {
			super(forward ? "Next Error" : "Previous Error",
					forward ? "Go to the next error line" : "Go to the previous error line",
					forward ? AllIcons.Actions.NextOccurence : AllIcons.Actions.PreviousOccurence);
			this.presentation = presentation;
			this.forward = forward;
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}

		@Override
		public void actionPerformed(@NotNull AnActionEvent event) {
			presentation.navigateToError(forward);
		}
	}
}
