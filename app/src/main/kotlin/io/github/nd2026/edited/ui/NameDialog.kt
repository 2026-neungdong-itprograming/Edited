package io.github.nd2026.edited.ui

import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.components.DialogWidget
import io.github.nd2026.edited.ui.components.TextFieldWidget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

/**
 * One-field dialog for naming things in the project tree (새로 만들기, 이름 바꾸기). [onSubmit] gets the
 * typed name and returns an error message to show under the field, or null to close the dialog.
 */
internal class NameDialog(
    title: String,
    private val hint: String,
    initial: String,
    placeholder: String,
    actionLabel: String,
    private val onSubmit: (String) -> String?,
) {
    private val field = TextFieldWidget(initial, accessibleLabel = title, placeholder = placeholder)
    private val body = Body()
    private val cancelButton = ButtonWidget("취소")
    private val okButton = ButtonWidget(actionLabel, ButtonWidget.Style.FILLED)
    private val dialog = DialogWidget(title, body, BODY_HEIGHT,listOf(cancelButton, okButton), cardWidth = 440)

    init {
        cancelButton.onClick = { dialog.close() }
        okButton.onClick = ::submit
        field.onSubmit = ::submit
        field.onChange = {
            field.invalid = false
            body.error = null
            body.requestRepaint()
        }
    }

    fun show(host: OverlayHostWidget) {
        dialog.show(host)
        host.hostPane?.focusManager?.requestFocus(field)
    }

    private fun submit() {
        val error = onSubmit(field.text.trim())
        if (error == null) {
            dialog.close()
        } else {
            field.invalid = true
            body.error = error
            body.requestRepaint()
        }
    }

    private inner class Body : Container() {
        var error: String? = null

        init {
            addChild(field)
        }

        override fun layout() {
            field.setBounds(bounds.x, bounds.y + HINT_HEIGHT, bounds.width, TextFieldWidget.HEIGHT)
        }

        override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.font = theme.labelFont
            val fm = g.fontMetrics
            g.color = theme.outline
            g.drawString(hint, 4, fm.ascent + 2)
            error?.let {
                g.color = theme.error
                g.drawString(it, 4, HINT_HEIGHT + TextFieldWidget.HEIGHT + 8 + fm.ascent)
            }
        }
    }

    private companion object {
        const val HINT_HEIGHT = 24
        const val BODY_HEIGHT = HINT_HEIGHT + TextFieldWidget.HEIGHT + 34
    }
}
