package io.github.nd2026.edited.ui

import java.text.AttributedString
import kotlin.test.Test
import kotlin.test.assertEquals

class InputMethodBridgeTest {
    @Test
    fun `empty input method iterator produces empty text`() {
        assertEquals("", attributedTextToString(AttributedString("").iterator))
        assertEquals("", attributedTextToString(null))
    }

    @Test
    fun `input method iterator preserves composed Korean text`() {
        assertEquals("한글", attributedTextToString(AttributedString("한글").iterator))
    }
}
