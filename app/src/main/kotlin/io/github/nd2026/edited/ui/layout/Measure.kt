package io.github.nd2026.edited.ui.layout

data class IntSize(val width: Int, val height: Int) {
    init {
        require(width >= 0 && height >= 0) { "size must not be negative" }
    }

    companion object {
        val Zero = IntSize(0, 0)
    }
}

data class Constraints(
    val minWidth: Int = 0,
    val maxWidth: Int = Int.MAX_VALUE,
    val minHeight: Int = 0,
    val maxHeight: Int = Int.MAX_VALUE,
) {
    init {
        require(minWidth >= 0 && minHeight >= 0) { "minimum size must not be negative" }
        require(maxWidth >= minWidth && maxHeight >= minHeight) { "maximum must be at least minimum" }
    }

    fun constrain(size: IntSize): IntSize = IntSize(
        size.width.coerceIn(minWidth, maxWidth),
        size.height.coerceIn(minHeight, maxHeight),
    )
}
