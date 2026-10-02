package io.github.nd2026.edited.ui.editor

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.lexer.DefaultMorphemeAnalyzer
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.TextAreaWidget
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * A full editor view of one [EditorDocument]: breadcrumb on top, gutter on the left, the text in the
 * middle and the inspection stripe on the right. Several panes can show the same document (split
 * editor); they share its [TextArea], so edits, undo history and the caret stay in sync between them.
 */
class EditorPaneWidget(
    val document: EditorDocument,
    analyzer: MorphemeAnalyzer = DefaultMorphemeAnalyzer,
) : Container() {

    val editor = TextAreaWidget(document.textArea, analyzer)
    private val gutter = GutterWidget(editor, document.inspections)
    private val stripe = InspectionStripeWidget(editor, document.inspections)
    private val breadcrumb = BreadcrumbWidget(
        textArea = document.textArea,
        documentTitle = { document.title },
        onNavigate = ::goToLine,
    )

    private var gutterWidth = 0
    private val syncViews = { refreshViews() }

    private val textListener = object : TextAreaListener {
        override fun onTextChanged(edit: TextEdit) {
            // Only a changed number of digits resizes the gutter; any other edit just repaints it.
            if (gutter.preferredWidth() != gutterWidth) layout()
            refreshViews(force = true)
        }

        override fun onCaretMoved(offset: Int) = refreshViews()
    }

    init {
        listOf(breadcrumb, gutter, editor, stripe).forEach(::addChild)
    }

    override fun onAttach() {
        document.textArea.addListener(textListener)
        document.inspections.addListener(syncViews)
        editor.addScrollListener(syncViews)
        breadcrumb.refresh(force = true)
        editor.onAttach() // Widget.addChild only attaches direct children, so propagate explicitly
    }

    override fun onDetach() {
        editor.onDetach()
        document.textArea.removeListener(textListener)
        document.inspections.removeListener(syncViews)
        editor.removeScrollListener(syncViews)
    }

    override fun layout() {
        val b = bounds
        val bodyTop = b.y + BREADCRUMB_HEIGHT
        val bodyHeight = (b.height - BREADCRUMB_HEIGHT).coerceAtLeast(1)
        gutterWidth = gutter.preferredWidth()
        val editorWidth = (b.width - gutterWidth - STRIPE_WIDTH).coerceAtLeast(1)
        breadcrumb.setBounds(b.x, b.y, b.width, BREADCRUMB_HEIGHT)
        gutter.setBounds(b.x, bodyTop, gutterWidth, bodyHeight)
        editor.setBounds(b.x + gutterWidth, bodyTop, editorWidth, bodyHeight)
        stripe.setBounds(b.x + b.width - STRIPE_WIDTH, bodyTop, STRIPE_WIDTH, bodyHeight)
        editor.scrollTo() // re-clamp the scroll position to the new size
    }

    private fun refreshViews(force: Boolean = false) {
        gutter.requestRepaint()
        stripe.requestRepaint()
        breadcrumb.refresh(force)
    }

    private fun goToLine(line: Int) {
        val textArea: TextArea = document.textArea
        val target = line.coerceIn(0, textArea.lineCount - 1)
        textArea.breakUndoGroup()
        textArea.moveCaretTo(textArea.lineStart(target))
        editor.scrollToLine(target)
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
    }

    companion object {
        const val BREADCRUMB_HEIGHT = 28
        const val STRIPE_WIDTH = 14
    }
}
