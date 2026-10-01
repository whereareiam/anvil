package me.whereareiam.anvil.integration.intellij.view.window.main.component.details

import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.util.IdentityHashMap

/**
 * Lays out detail sections in two bounded columns that collapse into one in narrow tool windows.
 *
 * Components added with the constraint `true` span the full width, such as the title and description.
 */
internal class DetailsLayout : LayoutManager2 {
	private val fullRows = IdentityHashMap<Component, Boolean>()

	private fun measure(parent: Container, width: Int, layout: Boolean): Int {
		val insets = parent.insets
		val available = maxOf(1, minOf(JBUI.scale(1000), width - insets.left - insets.right))
		val gap = JBUI.scale(32)
		val columns = if (available >= JBUI.scale(740)) 2 else 1
		val columnWidth = maxOf(1, (available - gap * (columns - 1)) / columns)
		val nextRow = intArrayOf(insets.top, insets.top)
		for (component in parent.components) {
			if (!component.isVisible) continue
			if (fullRows[component] == true) {
				val y = maxOf(nextRow[0], nextRow[1])
				val height = measuredHeight(component, available)
				if (layout) component.setBounds(insets.left, y, available, height)
				val following = y + height + JBUI.scale(if (component is DetailText && component.tone == TextTone.TITLE) 8 else 20)
				nextRow.fill(following)
				continue
			}

			val column = if (columns == 1 || component is DetailSection && component.primary) 0 else 1
			val height = measuredHeight(component, columnWidth)
			if (layout) component.setBounds(insets.left + column * (columnWidth + gap), nextRow[column], columnWidth, height)

			nextRow[column] += height + JBUI.scale(24)
		}

		return maxOf(insets.top, maxOf(nextRow[0], nextRow[1]) - JBUI.scale(16)) + insets.bottom
	}

	override fun preferredLayoutSize(parent: Container): Dimension {
		val width = parent.width.takeIf { it > 0 } ?: JBUI.scale(720)
		return Dimension(width, measure(parent, width, false))
	}

	override fun minimumLayoutSize(parent: Container) = Dimension(JBUI.scale(180), 0)

	override fun maximumLayoutSize(parent: Container) = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

	override fun layoutContainer(parent: Container) {
		measure(parent, parent.width, true)
	}

	override fun addLayoutComponent(component: Component, constraints: Any?) {
		fullRows[component] = constraints == true
	}

	override fun addLayoutComponent(name: String?, component: Component) = addLayoutComponent(component, null)

	override fun removeLayoutComponent(component: Component) {
		fullRows.remove(component)
	}

	override fun getLayoutAlignmentX(parent: Container) = 0f

	override fun getLayoutAlignmentY(parent: Container) = 0f

	override fun invalidateLayout(parent: Container) {}
}
