package com.spendlens.feature.expenses.list

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A running sync wins over the time of the last one: while it runs, the old time is about to be stale.
 *
 * Absolute times rather than "5 minutes ago". A relative time is out of date a minute after it is
 * drawn unless the screen redraws on a timer, and "Synced 9:41 AM" stays true however long it sits.
 */
internal fun syncStatusOf(
    isSyncing: Boolean,
    lastSyncedAt: Instant?,
    clock: Clock,
    zoneId: ZoneId,
    locale: Locale,
): SyncStatusUiModel =
    when {
        isSyncing -> SyncStatusUiModel.Syncing
        lastSyncedAt == null -> SyncStatusUiModel.NeverSynced
        else -> {
            val synced = lastSyncedAt.atZone(zoneId)
            val formatter = if (synced.toLocalDate() == LocalDate.now(clock.withZone(zoneId))) {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            } else {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            }
            SyncStatusUiModel.SyncedAt(formatter.withLocale(locale).format(synced))
        }
    }
