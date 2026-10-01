package me.whereareiam.anvil.integration.intellij.view.window.main.environment

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ShadowAction
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicTabbedPaneUI

/**
 * Services-style section navigation with a flat selected underline.
 */
class EnvironmentSectionTabs : JBTabbedPane(TOP, SCROLL_TAB_LAYOUT) {
	init {
		isOpaque = false
		accessibleContext?.accessibleName = "Scenario sections"
	}

	fun registerNavigation(target: JComponent, owner: Disposable) {
		registerNavigation(IdeActions.ACTION_NEXT_TAB, "navigateNext", target, owner)
		registerNavigation(IdeActions.ACTION_PREVIOUS_TAB, "navigatePrevious", target, owner)
	}

	private fun registerNavigation(actionId: String, swingAction: String, target: JComponent, owner: Disposable) {
		val action = object : DumbAwareAction() {
			override fun actionPerformed(event: AnActionEvent) {
				val navigation: Action = actionMap.get(swingAction)
				navigation.actionPerformed(
					ActionEvent(
						this@EnvironmentSectionTabs,
						ActionEvent.ACTION_PERFORMED,
						swingAction
					)
				)
			}

			override fun update(event: AnActionEvent) {
				event.presentation.isEnabled = tabCount > 1
			}

			override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
		}

		ShadowAction(action, actionId, target, owner).reconnect(ActionManager.getInstance().getAction(actionId))
	}

	/**
	 * Returns the width every section tab needs without scrolling.
	 */
	fun runWidth(): Int = (ui as? SectionTabsUi)?.runWidth() ?: preferredSize.width

	override fun updateUI() {
		setUI(SectionTabsUi())
		font = UIUtil.getLabelFont()
		background = JBUI.CurrentTheme.ToolWindow.background()
		border = JBUI.Borders.empty()
	}

	private class SectionTabsUi : BasicTabbedPaneUI() {
		override fun installDefaults() {
			super.installDefaults()
			tabInsets = JBUI.insets(0, 10)
			tabAreaInsets = JBUI.emptyInsets()
			contentBorderInsets = JBUI.emptyInsets()
			selectedTabPadInsets = JBUI.emptyInsets()
		}

		fun runWidth(): Int {
			val metrics = fontMetrics
			val tabs = (0 until tabPane.tabCount).sumOf { calculateTabWidth(TOP, it, metrics) }

			return tabs + tabAreaInsets.left + tabAreaInsets.right
		}

		override fun createScrollButton(direction: Int) = SectionScrollButton(direction)
		override fun getTabLabelShiftX(placement: Int, index: Int, selected: Boolean) = 0
		override fun getTabLabelShiftY(placement: Int, index: Int, selected: Boolean) = 0
		override fun paintContentBorder(graphics: Graphics, placement: Int, selected: Int) {}
		override fun paintFocusIndicator(
			graphics: Graphics,
			placement: Int,
			rectangles: Array<Rectangle>,
			index: Int,
			icon: Rectangle,
			text: Rectangle,
			selected: Boolean
		) {
		}

		override fun calculateTabHeight(placement: Int, index: Int, fontHeight: Int): Int {
			val border = getContentBorderInsets(placement)
			return JBUI.scale(JBUI.CurrentTheme.DebuggerTabs.tabHeight()) - border.top - border.bottom
		}

		override fun paintTabBackground(
			graphics: Graphics,
			placement: Int,
			index: Int,
			x: Int,
			y: Int,
			width: Int,
			height: Int,
			selected: Boolean
		) {
			graphics.color = tabPane.background
			graphics.fillRect(x, y, width, height)
		}

		override fun paintTabBorder(
			graphics: Graphics,
			placement: Int,
			index: Int,
			x: Int,
			y: Int,
			width: Int,
			height: Int,
			selected: Boolean
		) {
			if (!selected) return

			val underline = JBUI.CurrentTheme.TabbedPane.SELECTION_HEIGHT.get()
			graphics.color = if (tabPane.isEnabled) JBUI.CurrentTheme.TabbedPane.ENABLED_SELECTED_COLOR
			else JBUI.CurrentTheme.TabbedPane.DISABLED_SELECTED_COLOR

			graphics.fillRect(x, y + height - underline, width, underline)
		}
	}

	private class SectionScrollButton(direction: Int) :
		JButton(if (direction == WEST) AllIcons.Actions.Back else AllIcons.Actions.Forward), UIResource {
		init {
			val description = if (direction == WEST) "Scroll sections left" else "Scroll sections right"
			toolTipText = description
			accessibleContext?.accessibleName = description
			border = JBUI.Borders.empty(4)
			isContentAreaFilled = false
			isFocusable = false
		}

		override fun getPreferredSize(): Dimension = JBUI.size(24)
	}
}
