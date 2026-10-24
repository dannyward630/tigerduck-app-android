package org.ntust.app.tigerduck.data.preferences

/**
 * The way an accent gradient runs, from its start color to its end color, as
 * picked on the accent color page, in the order the page offers them.
 *
 * [code] is what the preference stores: fixed per direction, so reordering
 * them never changes a stored pick. The start and end sit at fractions of the
 * shape's width and height. They are the screen's own sides, not the reading
 * direction's: left to right starts at the left edge in a right-to-left
 * language too, as the arrow that shows it does.
 */
enum class AccentGradientDirection(
    val code: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
) {
    TopToBottom(0, 0f, 0f, 0f, 1f),
    BottomToTop(1, 0f, 1f, 0f, 0f),
    LeftToRight(2, 0f, 0f, 1f, 0f),
    RightToLeft(3, 1f, 0f, 0f, 0f),
    TopRightToBottomLeft(4, 1f, 0f, 0f, 1f),
    TopLeftToBottomRight(5, 0f, 0f, 1f, 1f);

    companion object {
        /** A gradient picked before any direction was: the wallpaper swatch's own, top left to bottom right. */
        val DEFAULT = TopLeftToBottomRight

        /** The direction stored as [code]; one this build does not know reads as [DEFAULT]. */
        fun fromCode(code: Int): AccentGradientDirection = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
