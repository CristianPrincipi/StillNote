package app.stillnote.ui

import app.stillnote.data.SyncStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

/** The few words added to a header's summary line, or null when there is nothing to say. */
val SyncStatus.label: String?
    get() = when (this) {
        SyncStatus.Idle -> null
        SyncStatus.Syncing -> "syncing"
        SyncStatus.Offline -> "offline"
        SyncStatus.Failed -> "couldn't sync"
    }

/** [summary] with the sync status after it, as in "4 to do · 2 done · offline". */
fun SyncStatus.after(summary: String): String = label?.let { "$summary · $it" } ?: summary

/** Holds "syncing" back unless it lasts, so a sync that takes a moment doesn't flash in the header. */
@OptIn(FlowPreview::class)
fun Flow<SyncStatus>.calm(): Flow<SyncStatus> = debounce { if (it == SyncStatus.Syncing) 800L else 0L }
