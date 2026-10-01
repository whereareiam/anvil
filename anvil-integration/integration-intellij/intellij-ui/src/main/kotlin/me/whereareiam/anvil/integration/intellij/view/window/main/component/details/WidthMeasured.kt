package me.whereareiam.anvil.integration.intellij.view.window.main.component.details

import java.awt.Component

/**
 * A detail component whose height depends on the width it is laid out in, such as wrapped text.
 */
internal interface WidthMeasured {
	fun heightForWidth(width: Int): Int
}

internal fun measuredHeight(component: Component, width: Int): Int =
	if (component is WidthMeasured) component.heightForWidth(width) else component.preferredSize.height
