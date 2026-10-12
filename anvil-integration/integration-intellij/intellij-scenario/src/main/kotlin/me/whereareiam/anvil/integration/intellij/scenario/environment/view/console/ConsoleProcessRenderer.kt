package me.whereareiam.anvil.integration.intellij.scenario.environment.view.console

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.scenario.window.ScenarioPresentation
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusIcon
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusPresentation
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
			val state = session.status
			val statusLabel = StatusPresentation.environmentLabel(state)
			icon = StatusIcon.overlay(StatusPresentation.environmentTone(state), statusLabel, ScenarioPresentation.scenarioIcon())
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
