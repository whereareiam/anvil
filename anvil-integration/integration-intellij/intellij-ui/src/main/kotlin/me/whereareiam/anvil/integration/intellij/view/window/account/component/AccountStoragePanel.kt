package me.whereareiam.anvil.integration.intellij.view.window.account.component

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * Storage controls emit requests; the Java controller owns path resolution and persistence.
 */
class AccountStoragePanel(
	project: Project,
	location: String,
	applyLocation: Consumer<String>,
	defaultLocation: Supplier<String>
) : JPanel(BorderLayout()) {
	private val directory = TextFieldWithBrowseButton().apply {
		text = location
		addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
		textField.accessibleContext.accessibleName = "Project account directory"
	}
	private val status = JBLabel(" ")
	private lateinit var apply: JButton
	private val body = panel {
		row("Project account directory:") { cell(directory).align(AlignX.FILL) }
		row { label("Changing the location does not move existing account files.") }
		row {
			apply = button("Apply Location") { applyLocation.accept(directory.text) }.component
			button("Use Default") {
				directory.text = defaultLocation.get()
				status.text = "Select Apply location to use the default."
			}
		}
		row { cell(status) }
	}.apply { isVisible = false }
	private val toggle = JToggleButton("▸ Storage").apply {
		isBorderPainted = false
		isContentAreaFilled = false
		horizontalAlignment = JButton.LEFT
		addActionListener {
			body.isVisible = isSelected
			text = if (isSelected) "▾ Storage" else "▸ Storage"
			revalidate()
		}
	}

	init {
		add(toggle, BorderLayout.NORTH)
		add(body, BorderLayout.CENTER)
	}

	fun showApplied(location: String) {
		directory.text = location
		status.text = "Location applied."
	}

	fun showFailure(message: String?) { status.text = message }

	fun setBusy(busy: Boolean) { apply.isEnabled = !busy }
}
