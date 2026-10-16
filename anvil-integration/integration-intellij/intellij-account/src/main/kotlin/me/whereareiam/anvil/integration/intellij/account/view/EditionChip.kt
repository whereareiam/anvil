package me.whereareiam.anvil.integration.intellij.account.view

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.api.type.AccountAvailability
import me.whereareiam.anvil.integration.intellij.account.AvailableAccount
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Small rounded tag naming a Minecraft edition an account is available for.
 */
class EditionChip(label: String) : JBLabel(label) {
	init {
		isOpaque = false
		border = JBUI.Borders.empty(1, 6)
	}

	override fun paintComponent(graphics: Graphics) {
		val painter = graphics.create() as Graphics2D
		try {
			painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			painter.color = BACKGROUND
			painter.fillRoundRect(0, 0, width, height, JBUI.scale(10), JBUI.scale(10))
		} finally { painter.dispose() }
		super.paintComponent(graphics)
	}

	override fun updateUI() {
		super.updateUI()
		font = JBFont.small()
		foreground = FOREGROUND
	}

	private companion object {
		val BACKGROUND = JBColor.namedColor("Tag.background", JBColor(0xDFE1E5, 0x393B40))
		val FOREGROUND = JBColor.namedColor("Label.infoForeground", JBColor(0x6C707E, 0x868A91))
	}
}

/**
 * Labels of the editions the account is available for, in the order its file recorded them: each opaque edition ID
 * with its first letter capitalised. Unavailable and unknown editions have no label.
 */
internal fun AvailableAccount.availableEditions(): List<String> = account.access
	.filter { it.availability == AccountAvailability.AVAILABLE }
	.map { access -> access.edition.id.replaceFirstChar { it.titlecase(Locale.ROOT) } }

/**
 * Lays out a component followed by one [EditionChip] per edition label, vertically centred.
 */
internal fun withEditionChips(component: JComponent, editions: List<String>): JPanel = JPanel().apply {
	layout = BoxLayout(this, BoxLayout.X_AXIS)
	isOpaque = false
	add(component)
	editions.forEachIndexed { index, edition ->
		add(Box.createHorizontalStrut(JBUI.scale(if (index == 0) 8 else 4)))
		add(EditionChip(edition))
	}
	add(Box.createHorizontalGlue())
}

/**
 * Accessible name of an account entry: its label followed by the editions it is available for.
 */
internal fun accessibleAccountName(label: String, editions: List<String>): String =
	if (editions.isEmpty()) label else "$label, available for ${editions.joinToString(", ")}"
