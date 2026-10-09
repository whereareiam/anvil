package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusIcon
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot
import java.awt.Font
import java.util.function.Supplier
import javax.swing.JList

/**
 * Renders process choices in the session console source list.
 */
class ConsoleProcessRenderer(
	private val session: EnvironmentSession,
	private val liveProcesses: Supplier<Map<String, ProcessSnapshot>>
) : ColoredListCellRenderer<ConsoleController.ProcessChoice>() {
	override fun customizeCellRenderer(
		list: JList<out ConsoleController.ProcessChoice>,
		choice: ConsoleController.ProcessChoice,
		index: Int,
		selected: Boolean,
		focused: Boolean
	) {
		setIpad(JBUI.insets(2, 0))
		setIconTextGap(JBUI.scale(4))
		setBorder(JBUI.Borders.empty())

		val snapshot = session.snapshot
		if (choice.name() == null) {
			val state = session.environmentState
			val statusLabel = StatusPresentation.environmentLabel(state)
			icon = StatusIcon.overlay(StatusPresentation.environmentTone(state), statusLabel, ScenarioPresentation.environmentIcon())
			append("", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)

			val labelWidth = getFontMetrics(font.deriveFont(Font.BOLD)).stringWidth(choice.displayName())
			val label = if (list.width > 0 && preferredSize.width + labelWidth > list.width) "All" else choice.displayName()
			append(label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)

			toolTipText = "${choice.displayName()} · ${StatusPresentation.environmentDescription(session.scenario, snapshot, state)}"

			return
		}

		val live = liveProcesses.get()[choice.name()]
		val statusLabel = StatusPresentation.processLabel(live?.state, snapshot.state)
		icon = StatusIcon.overlay(
			StatusPresentation.processTone(live?.state, snapshot.state),
			statusLabel,
			choice.role()?.let { ScenarioPresentation.processIcon(it) } ?: AllIcons.Debugger.Console
		)
		append(choice.displayName())

		toolTipText = "${choice.displayName()} · ${StatusPresentation.processDescription(live?.state, snapshot.state)}"
	}
}
