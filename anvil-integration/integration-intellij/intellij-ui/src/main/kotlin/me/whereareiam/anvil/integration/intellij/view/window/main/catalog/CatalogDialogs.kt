package me.whereareiam.anvil.integration.intellij.view.window.main.catalog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation
import me.whereareiam.anvil.integration.intellij.log.SessionLog
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JTextArea

/**
 * Kotlin owned transient catalog dialogs; the Java catalog controller supplies operations and data.
 */
object CatalogDialogs {
	@JvmStatic
	fun showPreparationOutput(project: Project, log: SessionLog, cancel: Runnable) {
		// Disposing through the tree releases the console editor with the presentation.
		val lifetime = Disposer.newDisposable("Anvil preparation output")
		try {
			val presentation = ConsolePresentation(project, log, cancel, lifetime)
			DialogBuilder(project).title("Scenario Preparation Output")
				.centerPanel(presentation.console.component)
				.resizable(true)
				.apply {
					removeAllActions()
					addCloseButton()
					show()
				}
		} finally {
			Disposer.dispose(lifetime)
		}
	}

	@JvmStatic
	fun showSyncDetails(project: Project, details: String, openBuild: Runnable) {
		val output = JTextArea(details, 24, 90).apply { isEditable = false }
		DialogBuilder(project).title("Project Sync Details")
			.centerPanel(JBScrollPane(output))
			.resizable(true)
			.apply {
				removeAllActions()
				addCloseButton()
				addLeftSideAction(object : AbstractAction("Open Build output") {
					override fun actionPerformed(event: ActionEvent?) {
						getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE)
						openBuild.run()
					}
				})
				show()
			}
	}
}
