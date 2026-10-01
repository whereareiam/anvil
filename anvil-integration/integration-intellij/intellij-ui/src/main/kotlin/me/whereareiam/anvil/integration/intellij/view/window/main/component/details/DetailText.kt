package me.whereareiam.anvil.integration.intellij.view.window.main.component.details

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import javax.swing.JTextArea
import javax.swing.text.View
import kotlin.math.ceil

internal enum class TextTone { VALUE, MUTED, TITLE }

/**
 * Selectable, wrapping detail text that follows the label theme for its tone.
 */
internal class DetailText(text: String, desiredTone: TextTone) : JTextArea(text), WidthMeasured {
	var tone: TextTone? = null
		private set

	init {
		tone = desiredTone
		isEditable = false
		isOpaque = false
		border = JBUI.Borders.empty()
		margin = JBUI.emptyInsets()
		lineWrap = true
		wrapStyleWord = true
		applyTheme()
	}

	override fun updateUI() {
		super.updateUI()
		// The superclass constructor calls updateUI before the tone is assigned.
		if (tone != null) applyTheme()
	}

	private fun applyTheme() {
		val labelFont = UIUtil.getLabelFont()
		font = if (tone == TextTone.TITLE) labelFont.deriveFont(Font.BOLD, labelFont.size2D + 2) else labelFont
		foreground = if (tone == TextTone.MUTED) UIUtil.getContextHelpForeground() else UIUtil.getLabelForeground()
		caretColor = UIUtil.getLabelForeground()
	}

	override fun heightForWidth(width: Int): Int {
		val view = getUI().getRootView(this)
		view.setSize(maxOf(1, width - insets.left - insets.right).toFloat(), Int.MAX_VALUE.toFloat())
		return ceil(view.getPreferredSpan(View.Y_AXIS).toDouble()).toInt() + insets.top + insets.bottom
	}
}
