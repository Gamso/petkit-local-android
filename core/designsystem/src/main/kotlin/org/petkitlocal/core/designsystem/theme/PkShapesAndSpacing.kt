package org.petkitlocal.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii of docs/DESIGN.md. */
@Immutable
data class PkShapes(
    val card: Shape = RoundedCornerShape(22.dp),
    val tile: Shape = RoundedCornerShape(16.dp),
    val button: Shape = RoundedCornerShape(14.dp),
    val field: Shape = RoundedCornerShape(14.dp),
    val chip: Shape = CircleShape,
    val sheet: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    val dialog: Shape = RoundedCornerShape(24.dp),
)

/** The 4 dp spacing scale, and the layout constants built on it. */
@Immutable
data class PkSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Left and right margin of a screen. */
    val gutter: Dp = 16.dp,
    /** Between two cards of a grid or a list. */
    val cardGap: Dp = 12.dp,
    /** The smallest touch target (SPEC.md §6, accessibility). */
    val minTouchTarget: Dp = 48.dp,
)
