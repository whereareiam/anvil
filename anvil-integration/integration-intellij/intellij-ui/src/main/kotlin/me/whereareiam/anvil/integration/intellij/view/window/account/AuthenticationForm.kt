package me.whereareiam.anvil.integration.intellij.view.window.account

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar

internal class AuthenticationForm {
	private val owner = Disposer.newDisposable("Anvil authentication form")
	private var closed = false
	private val status = JBLabel("Preparing account sign-in…")
	private val output = JBTextArea().apply {
		isEditable = false
		lineWrap = true
		wrapStyleWord = true
		border = JBUI.Borders.empty(8)
		accessibleContext.accessibleName = "Sign-in instructions and progress"
	}
	private val progress = JProgressBar().apply { isIndeterminate = true }
	private val links = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
	private lateinit var linkRow: Row
	private lateinit var progressRow: Row

	val component = panel {
		row { cell(status) }
		progressRow = row { cell(progress).align(AlignX.FILL) }
		linkRow = row { cell(links).align(AlignX.FILL) }.visible(false)
		row { scrollCell(output).align(Align.FILL) }.resizableRow()
	}.apply { preferredSize = JBUI.size(600, 320) }

	fun disposable(): Disposable = owner

	fun disposed(): Boolean = closed

	fun append(line: String) {
		output.append("$line\n")
		if (output.document.length > 64000)
			output.replaceRange("", 0, output.document.length - 64000)
		output.caretPosition = output.document.length
	}

	fun showSignIn(open: Runnable, copy: Runnable) {
		status.text = "Waiting for you to sign in in your browser…"
		links.removeAll()
		links.add(LinkLabel<Unit>("Open sign-in page", null) { _, _ -> open.run() })
		links.add(JButton("Copy link").apply { addActionListener { copy.run() } })
		linkRow.visible(true)
		links.revalidate()
	}

	fun complete(message: String) {
		progressRow.visible(false)
		linkRow.visible(false)
		status.text = message
	}
	fun disposeForm() {
		closed = true
		Disposer.dispose(owner)
	}
}
