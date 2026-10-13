package me.whereareiam.anvil.integration.intellij.view.window.account.component

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount
import me.whereareiam.anvil.integration.intellij.type.AccountSource
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.function.Consumer
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * Lists accounts available to the project and emits account requests to the accounts controller.
 */
class AccountListPanel(
	private val canSignIn: Boolean,
	global: Boolean,
	addAccount: Runnable,
	importAccount: Runnable,
	exportAccount: Runnable,
	removeAccount: Runnable,
	refresh: Runnable,
	sourcesChanged: Consumer<Boolean>
) : JPanel(BorderLayout(0, 12)) {
	private val model = AccountsModel()
	private val table = JBTable(model)
	private val addAccount = button("Add account…", addAccount)
	private val importFile = button("Import…", importAccount)
	private val export = button("Export…", exportAccount)
	private val remove = button("Remove", removeAccount)
	private val emptyAdd = button("Add account…", addAccount)
	private val emptyImport = button("Import account…", importAccount)
	private val cards = CardLayout()
	private val content = JPanel(cards)
	private val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
		listOf(this@AccountListPanel.addAccount, importFile, export, remove).forEach { add(it) }
	}
	private var busy = false

	init {
		border = JBUI.Borders.empty(12)
		table.apply {
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
			rowHeight = JBUI.scale(28)
			setShowGrid(false)
			autoCreateRowSorter = true
			selectionModel.addListSelectionListener { updateActions() }
			accessibleContext.accessibleName = "Accounts available to this project"
			columnModel.getColumn(0).cellRenderer = AccountRenderer(this@AccountListPanel.model)
		}
		content.add(JBScrollPane(table).apply { setColumnHeaderView(table.tableHeader) }, "accounts")
		content.add(emptyState(), "empty")
		val includeGlobal = JBCheckBox("Include global accounts in this project", global).apply {
			addActionListener { sourcesChanged.accept(isSelected) }
		}
		add(toolbar, BorderLayout.NORTH)
		add(content, BorderLayout.CENTER)
		add(JPanel(BorderLayout()).apply {
			add(includeGlobal, BorderLayout.WEST)
			add(button("Refresh", refresh), BorderLayout.EAST)
		}, BorderLayout.SOUTH)
		setAccounts(emptyList())
	}

	private fun emptyState() = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		add(Box.createVerticalGlue())
		add(JBLabel("No accounts available to this project").apply {
			font = font.deriveFont(Font.BOLD)
			alignmentX = CENTER_ALIGNMENT
		})
		add(Box.createVerticalStrut(8))
		val hint = if (canSignIn) "Sign in or import an existing Anvil account file."
			else "Import an account, or select a scenario source in Scenarios to sign in."
		add(JBLabel(hint).apply { alignmentX = CENTER_ALIGNMENT })
		add(JPanel(FlowLayout(FlowLayout.CENTER, 8, 12)).apply {
			add(emptyAdd)
			add(emptyImport)
			maximumSize = Dimension(Int.MAX_VALUE, 60)
		})
		add(Box.createVerticalGlue())
	}

	fun setAccounts(accounts: List<AvailableAccount>) {
		val previous = selected()
		model.accounts = accounts.toList()
		model.fireTableDataChanged()
		val index = accounts.indexOfFirst { it.file == previous?.file }
		if (index >= 0) {
			val row = table.convertRowIndexToView(index)
			table.setRowSelectionInterval(row, row)
		}
		cards.show(content, if (accounts.isEmpty()) "empty" else "accounts")
		toolbar.isVisible = accounts.isNotEmpty()
		updateActions()
	}

	fun selected(): AvailableAccount? = table.selectedRow.takeIf { it >= 0 }
		?.let { model.accounts[table.convertRowIndexToModel(it)] }

	fun setBusy(busy: Boolean) {
		this.busy = busy
		updateActions()
	}

	private fun updateActions() {
		for (button in listOf(addAccount, emptyAdd)) {
			button.isEnabled = canSignIn && !busy
			button.toolTipText = if (canSignIn) "Save a new account to this project"
				else "Select a scenario source in Scenarios to sign in"
		}
		importFile.isEnabled = !busy
		emptyImport.isEnabled = !busy
		val selected = selected()
		export.isEnabled = selected != null && !busy
		remove.isEnabled = selected?.source == AccountSource.PROJECT && !busy
		remove.toolTipText = "Only project accounts can be removed here"
	}

	private fun button(label: String, action: Runnable) = JButton(label).apply { addActionListener { action.run() } }

	private class AccountsModel : AbstractTableModel() {
		var accounts: List<AvailableAccount> = emptyList()
		private val columns = listOf("Account", "Local ID", "Source")
		override fun getRowCount() = accounts.size
		override fun getColumnCount() = columns.size
		override fun getColumnName(column: Int) = columns[column]
		override fun getValueAt(row: Int, column: Int): Any {
			val entry = accounts[row]
			return when (column) {
				0 -> entry.profileNames() ?: "Profile not available"
				1 -> entry.account.accountId
				else -> entry.source.label
			}
		}
	}

	/**
	 * Shows the account's profile names followed by a chip for each edition it is available for.
	 */
	private class AccountRenderer(private val model: AccountsModel) : TableCellRenderer {
		private val name = DefaultTableCellRenderer()

		override fun getTableCellRendererComponent(
			table: JTable,
			value: Any?,
			selected: Boolean,
			focused: Boolean,
			row: Int,
			column: Int
		): Component {
			val label = name.getTableCellRendererComponent(table, value, selected, focused, row, column) as JComponent
			val editions = model.accounts[table.convertRowIndexToModel(row)].availableEditions()
			val cell = withEditionChips(label, editions)
			cell.isOpaque = true
			cell.background = label.background
			cell.border = label.border
			label.border = null
			label.isOpaque = false
			cell.accessibleContext.accessibleName = accessibleAccountName(value.toString(), editions)
			// A renderer pane validates only while the table is displayable, so offscreen painting needs the layout now.
			cell.size = table.getCellRect(row, column, false).size
			cell.doLayout()

			return cell
		}
	}
}

/**
 * The distinct profile names the account signs in as in the editions it is available for, or those its file recorded
 * per protocol library before the runtime records editions, or null when none is known yet.
 */
internal fun AvailableAccount.profileNames(): String? = (account.access.mapNotNull { it.username } + libraryProfiles.map { it.username })
	.distinct()
	.takeIf { it.isNotEmpty() }
	?.joinToString(" / ")
