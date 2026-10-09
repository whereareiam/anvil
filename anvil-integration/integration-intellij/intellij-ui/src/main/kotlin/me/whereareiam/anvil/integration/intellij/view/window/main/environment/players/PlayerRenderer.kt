package me.whereareiam.anvil.integration.intellij.view.window.main.environment.players

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusIcon
import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor
import java.util.function.Function
import javax.swing.JList

/**
 * Renders retained players with a status overlay; players that left the environment are grayed.
 */
class PlayerRenderer(
	private val status: Function<PlayerDescriptor, PlayerStatus>
) : ColoredListCellRenderer<PlayerDescriptor>() {
	override fun customizeCellRenderer(
		list: JList<out PlayerDescriptor>,
		player: PlayerDescriptor?,
		index: Int,
		selected: Boolean,
		focused: Boolean
	) {
		if (player == null) return
		setIpad(JBUI.insets(2, 0))
		val current = status.apply(player)
		icon = StatusIcon.overlay(current.tone, current.label, AllIcons.General.User)
		append(
			player.displayName,
			if (current == PlayerStatus.PRESENT) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES
		)
		toolTipText = "${player.displayName} · ${current.label}"
		accessibleContext?.accessibleDescription = toolTipText
	}
}
