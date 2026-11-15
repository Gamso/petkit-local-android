package org.petkitlocal.feature.device.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.ActionLayout
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R

/**
 * A device page with its action sheet (SPEC.md §5.3) pinned at the bottom: [content] gets the
 * height of the collapsed sheet (and of [banner], when one shows) as bottom padding, so nothing
 * is hidden behind it. Expanded, the sheet lays a scrim over the page; a tap on the scrim, Back
 * or a drag down collapses it.
 */
@Composable
fun DeviceActionScaffold(
    actions: List<DeviceAction>,
    onAction: (DeviceAction) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    banner: @Composable () -> Unit = {},
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var bottomPadding by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val quick = LocalQuickActions.current
    val layout = remember(actions, quick.pinned) { DeviceActionOrder.layout(actions, quick.pinned) }
    BackHandler(enabled = expanded) { expanded = false }
    Box(modifier.fillMaxSize()) {
        content(bottomPadding)
        if (expanded) {
            val hide = stringResource(R.string.device_actions_hide)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SCRIM)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = hide,
                        onClick = { expanded = false },
                    ),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    // The page is padded for the collapsed sheet; expanding covers it on purpose.
                    if (!expanded) bottomPadding = with(density) { size.height.toDp() }
                },
        ) {
            banner()
            if (actions.isEmpty() || !quick.ready) {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            } else {
                DeviceActionSheet(
                    layout = layout,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onAction = { action ->
                        expanded = false
                        onAction(action)
                    },
                    onEditQuickActions = quick.onEdit?.let { edit ->
                        {
                            expanded = false
                            edit()
                        }
                    },
                )
            }
        }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.32f)

/**
 * The sheet itself, stateless. Collapsed, one row: the [DeviceActionOrder.VISIBLE] everyday
 * actions (or the user's own, [LocalQuickActions]) and, when there are others, "More". Expanded,
 * the others follow as a list (full width, so long names wrap between words), maintenance and
 * destructive ones last, then [onEditQuickActions] when the row can be edited: More shows then
 * even when every action is in view, so the editor can always be reached.
 */
@Composable
fun DeviceActionSheet(
    layout: ActionLayout,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAction: (DeviceAction) -> Unit,
    modifier: Modifier = Modifier,
    onEditQuickActions: (() -> Unit)? = null,
) {
    val labels = rememberServerLabels()
    val hasMore = layout.more.isNotEmpty() || onEditQuickActions != null
    val title = stringResource(org.petkitlocal.core.designsystem.R.string.pk_action_sheet)
    Surface(
        shape = PkTheme.shapes.sheet,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title }
            .testTag(DeviceTestTags.ACTION_SHEET),
    ) {
        Column(Modifier.padding(horizontal = PkTheme.spacing.m)) {
            if (hasMore) {
                Handle(expanded, onExpandedChange)
            } else {
                Spacer(Modifier.padding(top = PkTheme.spacing.s))
            }
            Row(Modifier.fillMaxWidth()) {
                layout.main.forEach { action ->
                    ActionTile(
                        label = labels.action(action.key, action.name),
                        icon = actionIcon(action.key),
                        // A destructive action pinned in view is marked as in the list, never primary.
                        primary = action == layout.main.first() && !action.destructive,
                        destructive = action.destructive,
                        onClick = { onAction(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // The row shares its width among what it holds: no empty column in the middle.
                if (hasMore) MoreTile(expanded, onExpandedChange, Modifier.weight(1f))
            }
            AnimatedVisibility(visible = expanded && hasMore) {
                Column {
                    MoreList(layout.more, labels, onAction)
                    onEditQuickActions?.let { EditQuickActionsEntry(it) }
                }
            }
            Spacer(Modifier.padding(bottom = PkTheme.spacing.s))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** The pull handle: drag it up or down, or tap it; TalkBack reads what it does and the state. */
@Composable
private fun Handle(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    var dragged by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { DRAG_THRESHOLD.toPx() }
    val action = stringResource(if (expanded) R.string.device_actions_hide else R.string.device_actions_show_all)
    val state = stringResource(if (expanded) R.string.device_actions_expanded else R.string.device_actions_collapsed)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = HANDLE_TOUCH_HEIGHT)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dragged += delta },
                onDragStarted = { dragged = 0f },
                onDragStopped = {
                    if (dragged < -threshold) onExpandedChange(true)
                    if (dragged > threshold) onExpandedChange(false)
                },
            )
            .testTag(DeviceTestTags.SHEET_HANDLE)
            .clickable(onClickLabel = action, role = Role.Button) { onExpandedChange(!expanded) }
            .clearAndSetSemantics {
                contentDescription = action
                stateDescription = state
                role = Role.Button
                onClick(label = action) {
                    onExpandedChange(!expanded)
                    true
                }
            },
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(PkTheme.colors.outline),
        )
    }
}

private val DRAG_THRESHOLD = 24.dp
private val HANDLE_TOUCH_HEIGHT = 28.dp

@Composable
private fun MoreTile(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier) {
    val action = stringResource(if (expanded) R.string.device_actions_hide else R.string.device_actions_show_all)
    val state = stringResource(if (expanded) R.string.device_actions_expanded else R.string.device_actions_collapsed)
    ActionTile(
        label = stringResource(if (expanded) R.string.device_actions_less else R.string.device_actions_more),
        icon = if (expanded) PkIcons.ExpandMore else PkIcons.MoreHoriz,
        primary = false,
        onClick = { onExpandedChange(!expanded) },
        modifier = modifier
            .semantics {
                contentDescription = action
                stateDescription = state
            }
            .testTag(DeviceTestTags.SHEET_MORE),
    )
}

/** One button of the row: a tile with the symbol, the name under it. */
@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = PkTheme.colors
    val (background, tint) = when {
        primary -> colors.accent to colors.onAccent
        destructive -> colors.warningContainer to colors.onWarningContainer
        else -> colors.surfaceMuted to colors.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(PkTheme.shapes.tile)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(PkTheme.spacing.xxs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(TILE_SIZE)
                .clip(PkTheme.shapes.tile)
                .background(background),
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.padding(top = PkTheme.spacing.xs))
        WholeWordLabel(label)
    }
}

private val TILE_SIZE = 56.dp

/**
 * A tile's name, centred, on up to three lines and never broken inside a word: when one word is
 * wider than the tile (a long French word at 130 %), the text is made smaller until it fits.
 */
@Composable
private fun WholeWordLabel(text: String) {
    val base = PkTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = constraints.maxWidth
        val scale = remember(text, width, base, measurer) {
            val words = text.split(' ').filter { it.isNotEmpty() }
            var scale = 1f
            fun fits(s: Float) = words.all { word ->
                val style = base.copy(fontSize = base.fontSize * s)
                measurer.measure(AnnotatedString(word), style, softWrap = false, maxLines = 1).size.width <= width
            }
            while (scale > MIN_LABEL_SCALE && !fits(scale)) scale -= LABEL_SCALE_STEP
            scale
        }
        Text(
            text,
            style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale),
            color = PkTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = MAX_LABEL_LINES,
        )
    }
}

private const val MIN_LABEL_SCALE = 0.6f
private const val LABEL_SCALE_STEP = 0.05f
private const val MAX_LABEL_LINES = 3

/** The other actions, one per line; destructive ones are marked and still confirmed. */
@Composable
private fun MoreList(actions: List<DeviceAction>, labels: ServerLabels, onAction: (DeviceAction) -> Unit) {
    val colors = PkTheme.colors
    Column(
        Modifier
            .heightIn(max = MORE_LIST_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = PkTheme.spacing.xs))
        actions.forEach { action ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                    .clip(PkTheme.shapes.tile)
                    .clickable(role = Role.Button) { onAction(action) }
                    .padding(horizontal = PkTheme.spacing.xs, vertical = PkTheme.spacing.xxs),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(LIST_ICON_SIZE)
                        .clip(PkTheme.shapes.tile)
                        .background(if (action.destructive) colors.warningContainer else colors.surfaceMuted),
                ) {
                    Icon(
                        actionIcon(action.key),
                        contentDescription = null,
                        tint = if (action.destructive) colors.onWarningContainer else colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(PkTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(labels.action(action.key, action.name), style = PkTheme.typography.bodyLarge)
                    if (action.destructive) {
                        Text(
                            stringResource(R.string.device_actions_confirmed),
                            style = PkTheme.typography.bodyMedium,
                            color = colors.onSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
}

/** The way to the quick actions editor, last in the expanded sheet. */
@Composable
private fun EditQuickActionsEntry(onEdit: () -> Unit) {
    PkButton(
        text = stringResource(R.string.quick_actions_edit),
        onClick = onEdit,
        style = PkButtonStyle.Text,
        icon = PkIcons.Edit,
        modifier = Modifier
            .padding(top = PkTheme.spacing.xxs)
            .testTag(QuickActionsTestTags.EDIT_ENTRY),
    )
}

private val LIST_ICON_SIZE = 40.dp
private val MORE_LIST_MAX_HEIGHT = 360.dp
