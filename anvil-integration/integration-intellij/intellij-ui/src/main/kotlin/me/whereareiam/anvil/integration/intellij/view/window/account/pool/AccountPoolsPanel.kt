package me.whereareiam.anvil.integration.intellij.view.window.account.pool

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Lists account pools with add, edit, and remove requests; persistence belongs to the accounts controller.
 */
class AccountPoolsPanel(addPool: Runnable, editPool: Consumer<String>, removePool: Consumer<String>) : JPanel(BorderLayout(0, 12)) {
	private var busy = false
	private var hasAccounts = false
	private val model = PoolsModel()
	private val table = JBTable(model).apply {
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
		rowHeight = JBUI.scale(28)
		setShowGrid(false)
		accessibleContext.accessibleName = "Project account pools"
		selectionModel.addListSelectionListener { updateActions() }
	}
	private val create = JButton("Create pool…").apply { addActionListener { addPool.run() } }
	private val edit = JButton("Edit…").apply { addActionListener { selected()?.let(editPool::accept) } }
	private val remove = JButton("Remove").apply { addActionListener { selected()?.let(removePool::accept) } }
	private val empty = JBLabel()

	init {
		border = JBUI.Borders.empty(12)
		add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
			listOf(create, edit, remove).forEach { add(it) }
		}, BorderLayout.NORTH)
		add(JBScrollPane(table).apply { setColumnHeaderView(table.tableHeader) }, BorderLayout.CENTER)
		add(empty, BorderLayout.SOUTH)
		setPools(emptyMap(), false)
	}

	fun setPools(pools: Map<String, List<String>>, hasAccounts: Boolean) {
		this.hasAccounts = hasAccounts
		model.pools = pools
		model.names = pools.keys.toList()
		model.fireTableDataChanged()
		create.toolTipText = if (hasAccounts) "Create a named group of accounts" else "Add accounts on the Accounts tab first"
		empty.isVisible = pools.isEmpty()
		empty.text = if (hasAccounts) "No pools yet. Group accounts to reference them together in scenarios."
			else "Add accounts on the Accounts tab, then create a pool."
		updateActions()
	}

	fun setBusy(busy: Boolean) {
		this.busy = busy
		updateActions()
	}

	private fun updateActions() {
		create.isEnabled = hasAccounts && !busy
		edit.isEnabled = selected() != null && !busy
		remove.isEnabled = selected() != null && !busy
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
