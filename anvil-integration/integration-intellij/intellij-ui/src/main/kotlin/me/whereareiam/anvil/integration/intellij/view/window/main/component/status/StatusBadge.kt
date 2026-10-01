package me.whereareiam.anvil.integration.intellij.view.window.main.component.status

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Rounded label that shows a status tone, label, and accessible description.
 *
 * In compact mode only the colored dot is shown; the label remains the accessible name and the
 * description remains the tooltip.
 */
class StatusBadge : JBLabel() {
	private var tone = StatusPresentation.Tone.NEUTRAL
	private var label = ""

	/**
	 * Hides the label text so narrow headers keep room for their other controls.
	 */
	var compact = false
		set(value) {
			if (field == value) return
			field = value
			text = if (value) null else label
		}

	init {
		show(StatusPresentation.Tone.NEUTRAL, "Not started", "Not started")
		isOpaque = false
		iconTextGap = JBUI.scale(4)
		border = JBUI.Borders.empty(2, 6)
	}

	fun update(scenario: ScenarioDescriptor, snapshot: SessionSnapshot, state: EnvironmentState) =
		show(
			StatusPresentation.environmentTone(state),
			StatusPresentation.environmentLabel(state),
			StatusPresentation.environmentDescription(scenario, snapshot, state)
		)

	fun show(tone: StatusPresentation.Tone, label: String, description: String) {
		this.tone = tone
		this.label = label
		text = if (compact) null else label
		icon = StatusIcon.dot(tone, label)
		toolTipText = description
		getAccessibleContext().accessibleName = label
		getAccessibleContext().accessibleDescription = description
	}

	/**
	 * Returns the width the badge needs to show its label, whether or not it is compact.
	 */
	fun fullWidth(): Int =
		insets.left + insets.right + (icon?.iconWidth ?: 0) + iconTextGap + getFontMetrics(font).stringWidth(label)

	override fun paintComponent(graphics: Graphics) {
		val painter = graphics.create() as Graphics2D
		try {
			painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			val color = StatusIcon.color(tone)
			painter.color = Color(color.red, color.green, color.blue, 28)
			painter.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
		} finally { painter.dispose() }
		super.paintComponent(graphics)
	}

	override fun updateUI() {
		super.updateUI()
		font = UIUtil.getLabelFont()
		foreground = UIUtil.getLabelForeground()
	}
}
