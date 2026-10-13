package me.whereareiam.anvil.integration.intellij.account.view.pool

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Account pools with the native list toolbar; persistence belongs to the accounts controller.
 */
class PoolsPanel(addPool: Runnable, editPool: Consumer<String>, removePool: Consumer<String>) : JPanel(BorderLayout()) {
	private var busy = false
	private var hasAccounts = false
	private val model = PoolsModel()
	private val table = JBTable(model).apply {
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
		setShowGrid(false)
		accessibleContext.accessibleName = "Project account pools"
	}

	init {
		val decorated = ToolbarDecorator.createDecorator(table)
			.setAddAction { addPool.run() }
			.setAddActionName("Create Pool")
			.setAddActionUpdater { !busy && hasAccounts }
			.setEditAction { selected()?.let(editPool::accept) }
			.setEditActionName("Edit Pool")
			.setEditActionUpdater { !busy && selected() != null }
			.setRemoveAction { selected()?.let(removePool::accept) }
			.setRemoveActionName("Remove Pool")
			.setRemoveActionUpdater { !busy && selected() != null }
			.disableUpDownActions()
			.createPanel()
		border = JBUI.Borders.emptyTop(8)
		add(decorated, BorderLayout.CENTER)
		setPools(emptyMap(), false)
	}

	fun setPools(pools: Map<String, List<String>>, hasAccounts: Boolean) {
		this.hasAccounts = hasAccounts
		model.pools = pools
		model.names = pools.keys.toList()
		model.fireTableDataChanged()
		table.emptyText.clear()
		if (hasAccounts) {
			table.emptyText.text = "No pools yet"
			table.emptyText.appendSecondaryText("Group accounts to lease them together in scenarios", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
			return
		}
		table.emptyText.text = "Add accounts on the Accounts tab, then create a pool"
	}

	fun setBusy(busy: Boolean) {
		this.busy = busy
		table.isEnabled = !busy
	}

	private fun selected(): String? = table.selectedRow.takeIf { it >= 0 }?.let { model.names[it] }

	private class PoolsModel : AbstractTableModel() {
		var pools: Map<String, List<String>> = emptyMap()
		var names: List<String> = emptyList()
		override fun getRowCount() = names.size
		override fun getColumnCount() = 2
		override fun getColumnName(column: Int) = if (column == 0) "Pool" else "Account IDs"
		override fun getValueAt(row: Int, column: Int): Any =
			if (column == 0) names[row] else pools.getValue(names[row]).joinToString(", ")
	}
}
