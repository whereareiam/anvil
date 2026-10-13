package me.whereareiam.anvil.integration.intellij.account.view

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.account.AccountSource
import me.whereareiam.anvil.integration.intellij.account.AvailableAccount
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * Accounts available to the project, with the native list toolbar; [AccountsController] performs the requests.
 */
class AccountListPanel(
	private val canSignIn: Boolean,
	signIn: Runnable,
	importAccount: Runnable,
	exportAccount: Runnable,
	removeAccount: Runnable,
	refresh: Runnable
) : JPanel(BorderLayout()) {
	private val model = AccountsModel()
	private val table = JBTable(model)
	private var busy = false

	init {
		table.apply {
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
			setShowGrid(false)
			autoCreateRowSorter = true
			accessibleContext.accessibleName = "Accounts available to this project"
			columnModel.getColumn(0).cellRenderer = AccountRenderer(this@AccountListPanel.model)
			emptyText.text = "No accounts available to this project"
			if (canSignIn) {
				emptyText.appendSecondaryText("Sign in", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { signIn.run() }
				emptyText.appendSecondaryText(" or ", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
			}
			emptyText.appendSecondaryText("import an account file", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { importAccount.run() }
		}
		val add = DefaultActionGroup(
			action("Sign In with Browser…", if (canSignIn) null else "Select a scenario source in the Anvil tool window to sign in") {
				signIn.run()
			}.also { it.enabledWhen { canSignIn } },
			action("Import Account File…", null) { importAccount.run() }
		)
		val decorated = ToolbarDecorator.createDecorator(table)
			.setAddAction { button ->
				JBPopupFactory.getInstance()
					.createActionGroupPopup(null, add, button.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
					.show(button.preferredPopupPoint)
			}
			.setAddActionName("Add Account")
			.setAddActionUpdater { !busy }
			.setRemoveAction { removeAccount.run() }
			.setRemoveActionName("Remove Project Account")
			.setRemoveActionUpdater { !busy && selected()?.source == AccountSource.PROJECT }
			.disableUpDownActions()
			.addExtraAction(button("Export Account File…", AllIcons.ToolbarDecorator.Export, { !busy && selected() != null }) {
				exportAccount.run()
			})
			.addExtraAction(button("Refresh", AllIcons.Actions.Refresh, { !busy }) { refresh.run() })
			.createPanel()
		border = JBUI.Borders.emptyTop(8)
		add(decorated, BorderLayout.CENTER)
	}

	fun setAccounts(accounts: List<AvailableAccount>) {
		val previous = selected()
		model.accounts = accounts.toList()
		model.fireTableDataChanged()
		val index = accounts.indexOfFirst { it.file == previous?.file }
		if (index < 0) return

		val row = table.convertRowIndexToView(index)
		table.setRowSelectionInterval(row, row)
	}

	fun selected(): AvailableAccount? = table.selectedRow.takeIf { it >= 0 }
		?.let { model.accounts[table.convertRowIndexToModel(it)] }

	fun setBusy(busy: Boolean) {
		this.busy = busy
		table.isEnabled = !busy
	}

	private fun action(text: String, description: String?, perform: () -> Unit) = object : DumbAwareAction(text, description, null) {
		var enabled: () -> Boolean = { true }
		fun enabledWhen(condition: () -> Boolean) { enabled = condition }
		override fun getActionUpdateThread() = ActionUpdateThread.EDT
		override fun update(event: AnActionEvent) { event.presentation.isEnabled = enabled() && !busy }
		override fun actionPerformed(event: AnActionEvent) = perform()
	}

	private fun button(text: String, icon: Icon, enabled: () -> Boolean, perform: () -> Unit) =
		object : DumbAwareAction(text, null, icon) {
			override fun getActionUpdateThread() = ActionUpdateThread.EDT
			override fun update(event: AnActionEvent) { event.presentation.isEnabled = enabled() }
			override fun actionPerformed(event: AnActionEvent) = perform()
		}

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
