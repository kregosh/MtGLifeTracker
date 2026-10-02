package com.kregosh.mtglifetracker.ui

import android.content.res.Resources
import com.kregosh.mtglifetracker.R
import com.kregosh.mtglifetracker.viewmodel.HomeError

internal fun HomeError.message(res: Resources): String {
    val unknown = res.getString(R.string.error_unknown)
    return when (this) {
        HomeError.SessionNotFound    -> res.getString(R.string.error_session_not_found)
        HomeError.RemovedFromSession -> res.getString(R.string.error_removed)
        HomeError.SessionEnded       -> res.getString(R.string.error_session_ended)
        is HomeError.SessionFull     -> res.getString(R.string.error_session_full, maxPlayers)
        is HomeError.CreateFailed    -> res.getString(R.string.error_create_failed, detail ?: unknown)
        is HomeError.JoinFailed      -> res.getString(R.string.error_join_failed, detail ?: unknown)
    }
}
