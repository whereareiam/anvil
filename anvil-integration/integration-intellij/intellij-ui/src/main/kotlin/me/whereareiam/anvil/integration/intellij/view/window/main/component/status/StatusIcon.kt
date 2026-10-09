package me.whereareiam.anvil.integration.intellij.view.window.main.component.status

import com.intellij.ui.JBColor
import com.intellij.ui.icons.IconWithToolTip
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Swing icon rendering for status values.
 */
object StatusIcon {
	@JvmStatic fun dot(tone: StatusPresentation.Tone, label: String): Icon = StatusIcon(null, tone, label)
	@JvmStatic fun overlay(tone: StatusPresentation.Tone, label: String, base: Icon): Icon =
		StatusIcon(base, tone, label)
	@JvmStatic fun color(tone: StatusPresentation.Tone): Color = when (tone) {
		StatusPresentation.Tone.NEUTRAL -> JBColor.namedColor("Anvil.Status.neutral", 0x8C909B, 0x868A91)
		StatusPresentation.Tone.RUNNING -> JBColor.namedColor("Anvil.Status.running", 0x208A3C, 0x5FAD65)
		StatusPresentation.Tone.TRANSITION -> JBColor.namedColor("Anvil.Status.transition", 0xB87900, 0xE9AB4B)
		StatusPresentation.Tone.FAILURE -> JBColor.namedColor("Anvil.Status.failure", 0xDB3B4B, 0xDB5C5C)
	}

	private class StatusIcon(
		private val base: Icon?,
		private val tone: StatusPresentation.Tone,
		private val label: String
	) : IconWithToolTip {
		override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
			base?.paintIcon(component, graphics, x, y)
			val painter = graphics.create() as Graphics2D
			try {
				painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
				val diameter = JBUI.scale(6)
				val left = x + iconWidth - diameter - JBUI.scale(1)
				val top = y + iconHeight - diameter - JBUI.scale(1)
				if (base != null) {
					painter.color = component?.background ?: JBUI.CurrentTheme.ToolWindow.background()
					val ring = JBUI.scale(1)
					painter.fillOval(left - ring, top - ring, diameter + ring * 2, diameter + ring * 2)
				}
				painter.color = color(tone)
				painter.fillOval(left, top, diameter, diameter)
			} finally { painter.dispose() }
		}
		override fun getIconWidth(): Int = base?.iconWidth ?: JBUI.scale(8)
		override fun getIconHeight(): Int = base?.iconHeight ?: JBUI.scale(8)
		override fun getToolTip(composite: Boolean): String = label
	}
}
