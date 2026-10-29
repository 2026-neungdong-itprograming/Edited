package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.lexer.Morpheme

/** 1-8. Double passives such as "열려졌다" (열리/VV + 어/EC + 지/VX) or "되어지다" → WARNING. */
class PassiveVoiceRule : MorphemeRule {
    override val id = "double-passive"

    // TODO(작업 1-8)
    override fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> = emptyList()
}
