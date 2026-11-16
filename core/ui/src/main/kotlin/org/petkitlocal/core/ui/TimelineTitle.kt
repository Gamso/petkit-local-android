package org.petkitlocal.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

/**
 * What a History card is about, as History and a camera's recent clips name it: the pet for a
 * visit, otherwise its kind, translated by key, with the pet when there is one.
 */
@Composable
fun timelineTitle(item: TimelineItem, labels: ServerLabels): String {
    val visit = TimelineCategory.Toileting in item.categories
    return if (visit) {
        item.pet?.name ?: stringResource(R.string.timeline_unknown_pet)
    } else {
        val kind = labels.eventKind(item.eventKind ?: item.kind, item.label)
        item.pet?.name?.let { "$kind · $it" } ?: kind
    }
}
