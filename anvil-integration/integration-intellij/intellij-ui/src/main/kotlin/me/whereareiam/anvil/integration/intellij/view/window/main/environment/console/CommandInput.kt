package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.FixedComboBoxEditor
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TextFieldWithHistory
import com.intellij.util.ui.StatusText
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.model.settings.CommandScope
import me.whereareiam.anvil.integration.intellij.settings.ProjectCommandHistory
import java.util.HashMap
import java.util.HashSet
import javax.swing.event.DocumentEvent

/**
 * Native history input with per-target drafts; selecting history never submits a command.
 */
class CommandInput(
	private val session: EnvironmentSession,
	label: String
) : TextFieldWithHistory(), Disposable {
	private val history = session.ideProject.getService(ProjectCommandHistory::class.java)
	private val textEditor = FixedComboBoxEditor()
	private val drafts = HashMap<CommandScope?, Draft>()
	private val pendingSubmissions = HashSet<PendingSubmission>()
	private var scope: CommandScope? = null
	private var displayedHistory = emptyList<String>()
	private var displayedLimit = -1
	private var changeSequence = 0L
	private var revision = 0L
	private var updating = false
	private var disposed = false

	init {
		Disposer.register(session, this)
		setEditor(textEditor)
		setPrototypeDisplayValue("Console command")
		accessibleContext?.accessibleName = label
		textEditor.field.accessibleContext?.accessibleName = label
		addDocumentListener(object : DocumentAdapter() {
			override fun textChanged(event: DocumentEvent) {
				if (!updating) revision = ++changeSequence
			}
		})

		history.subscribe(::refreshHistory, session)
		ApplicationManager.getApplication().messageBus.connect(session).subscribe(
			UISettingsListener.TOPIC,
			object : UISettingsListener {
				override fun uiSettingsChanged(uiSettings: UISettings) = refreshHistory()
			}
		)
	}

	fun getEmptyText(): StatusText = textEditor.field.emptyText

	fun onSubmit(submit: Runnable) {
		getTextEditor().addActionListener { if (!isPopupVisible) submit.run() }
	}

	fun scope(target: String?, operation: String) {
		val selected = target?.let {
			CommandScope.builder()
				.source(session.source.id)
				.definition(session.scenario.definition)
				.scenario(session.scenario.name)
				.target(it)
				.operation(operation)
				.build()
		}

		if (scope == selected) return
		drafts[scope] = draft()
		scope = selected
		applyHistory(drafts[scope] ?: Draft("", 0, 0, 0))
	}

	fun submit() {
		val submittedScope = scope ?: return
		if (!isEnabled || text.isBlank()) return

		val submitted = draft()
		val pending = PendingSubmission(submittedScope, submitted.revision, submitted.text)
		if (!pendingSubmissions.add(pending)) return

		session.console(submittedScope.target, submitted.text).whenComplete { accepted, failure ->
			ApplicationManager.getApplication().invokeLater({
				pendingSubmissions.remove(pending)
				if (failure != null || accepted != true || session.ideProject.isDisposed) return@invokeLater

				history.submitted(submittedScope, submitted.text)
				if (disposed) return@invokeLater

				if (scope == submittedScope) {
					if (revision == submitted.revision && text == submitted.text) text = ""
				} else {
					val saved = drafts[submittedScope]
					if (saved != null && saved.revision == submitted.revision && saved.text == submitted.text)
						drafts[submittedScope] = Draft("", 0, 0, ++changeSequence)
				}

			}, ModalityState.any())
		}
	}

	private fun refreshHistory() {
		if (disposed) return
		val available = scope?.let(history::history) ?: emptyList()
		if (displayedLimit == history.limit() && displayedHistory == available) return

		applyHistory(draft())
	}

	private fun applyHistory(draft: Draft) {
		updating = true
		try {
			displayedLimit = history.limit()
			displayedHistory = scope?.let(history::history) ?: emptyList()

			setHistorySize(displayedLimit)
			setHistory(displayedHistory)
			setSelectedItem(null)
			text = draft.text

			val caret = getTextEditor().caret
			caret.dot = minOf(draft.mark, draft.text.length)
			caret.moveDot(minOf(draft.dot, draft.text.length))

			revision = draft.revision
		} finally { updating = false }
	}

	override fun dispose() {
		disposed = true
	}

	private fun draft(): Draft {
		val caret = getTextEditor().caret
		return Draft(text, caret.dot, caret.mark, revision)
	}

	private data class Draft(val text: String, val dot: Int, val mark: Int, val revision: Long)
	private data class PendingSubmission(val key: CommandScope, val revision: Long, val text: String)
}
