package org.petkitlocal.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the meal of timeline card [id] put out, read from its event once: [amount] on a single
 * hopper, [amount1] and [amount2] on a Dual-Hopper. All null when the event said nothing of it,
 * which is still kept, so it is not read again. Not tied to `timeline_items`: a day refetched
 * replaces its cards, and the portions of a meal never change.
 */
@Entity(tableName = "served_portions")
data class ServedPortionsRow(@PrimaryKey val id: Long, val amount: Int?, val amount1: Int?, val amount2: Int?)
