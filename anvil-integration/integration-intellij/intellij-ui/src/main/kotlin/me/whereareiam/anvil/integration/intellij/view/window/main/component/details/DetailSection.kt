package me.whereareiam.anvil.integration.intellij.view.window.main.component.details

import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusBadge
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.util.function.Consumer
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * A titled group of labelled facts. Primary sections stay in the first column of the details layout.
 *
 * @param labelColumn preferred label column width shared by every section of the panel
 */
internal class DetailSection(
	title: String,
	val primary: Boolean,
	private val labelColumn: () -> Int
) : JPanel(null), WidthMeasured {
	init {
		isOpaque = false
		add(TitledSeparator(title).apply {
			border = JBUI.Borders.empty()
			isOpaque = false
		})
	}

	/**
	 * Reports whether the section contains only its title.
	 */
	fun isEmpty() = componentCount == 1

	/**
	 * Returns the preferred width of this section's widest fact label.
	 */
	fun widestLabel(): Int = components.filterIsInstance<Fact>().maxOfOrNull { it.labelWidth() } ?: 0

	fun fact(label: String, value: String?) {
		if (value.isNullOrBlank()) return
		add(Fact(label, DetailText(value, TextTone.VALUE), labelColumn))
	}

	fun status(tone: StatusPresentation.Tone, label: String, description: String) {
		add(Fact("State", StatusBadge().apply { show(tone, label, description) }, labelColumn))
	}

	fun link(label: String, text: String, target: String, copyLabel: String, open: Consumer<String>) {
		val link = LinkLabel<Unit>(text, null) { _, _ -> open.accept(target) }.apply {
			toolTipText = target
			isFocusable = true
		}
		val copy = object : AbstractAction(copyLabel) {
			override fun actionPerformed(event: ActionEvent?) {
				CopyPasteManager.getInstance().setContents(StringSelection(target))
			}
		}
		link.componentPopupMenu = JPopupMenu().apply { add(JMenuItem(copy)) }
		for (shortcut in CommonShortcuts.getCopy().shortcuts) {
			if (shortcut !is KeyboardShortcut || shortcut.secondKeyStroke != null) continue
			link.inputMap.put(shortcut.firstKeyStroke, "copyTarget")
		}
		link.actionMap.put("copyTarget", copy)
		add(Fact(label, link, labelColumn))
	}

	override fun heightForWidth(width: Int) = measure(width, false)

	override fun doLayout() {
		measure(width, true)
	}

	private fun measure(width: Int, layout: Boolean): Int {
		var y = 0
		for ((index, component) in components.withIndex()) {
			val height = measuredHeight(component, width)
			if (layout) component.setBounds(0, y, width, height)
			y += height + JBUI.scale(if (index == 0) 12 else 8)
		}
		return maxOf(0, y - JBUI.scale(8))
	}

	private class FactLabel(text: String) : JBLabel(text) {
		override fun updateUI() {
			super.updateUI()
			font = UIUtil.getLabelFont()
			foreground = UIUtil.getContextHelpForeground()
		}
	}

	/**
	 * Places the label beside its value, stacking both when the label or a status badge does not fit.
	 */
	private class Fact(
		name: String,
		private val value: JComponent,
		private val labelColumn: () -> Int
	) : JPanel(null), WidthMeasured {
		private val label = FactLabel(name)

		init {
			isOpaque = false
			label.labelFor = value
			value.accessibleContext.accessibleName = name
			add(label)
			add(value)
		}

		fun labelWidth() = label.preferredSize.width

		// The shared column fits the widest label, capped so values keep most of a narrow section.
		private fun labelWidth(width: Int) = minOf(labelColumn(), JBUI.scale(200), maxOf(1, width / 2))

		private fun stacked(width: Int) = label.preferredSize.width > labelWidth(width) ||
			value is StatusBadge && value.preferredSize.width > width - labelWidth(width) - JBUI.scale(12)

		override fun heightForWidth(width: Int): Int {
			if (stacked(width)) return label.preferredSize.height + JBUI.scale(6) + measuredHeight(value, width)
			return maxOf(label.preferredSize.height, measuredHeight(value, maxOf(1, width - labelWidth(width) - JBUI.scale(12))))
		}

		override fun doLayout() {
			val labelWidth = labelWidth(width)
			val x = labelWidth + JBUI.scale(12)
			val labelHeight = label.preferredSize.height
			if (stacked(width)) {
				label.setBounds(0, 0, width, labelHeight)
				val valueWidth = if (value is StatusBadge) minOf(value.preferredSize.width, width) else width
				value.setBounds(0, labelHeight + JBUI.scale(6), valueWidth, measuredHeight(value, valueWidth))
				return
			}

			label.setBounds(0, 0, labelWidth, labelHeight)
			if (value is StatusBadge) {
				val preferred = value.preferredSize
				value.setBounds(x, 0, maxOf(1, minOf(preferred.width, width - x)), preferred.height)
				return
			}

			val valueWidth = maxOf(1, width - x)
			value.setBounds(x, 0, valueWidth, measuredHeight(value, valueWidth))
		}
	}
}
