package com.kregosh.mtglifetracker.ui

import com.kregosh.mtglifetracker.viewmodel.HomeError

internal fun HomeError.message(): String {
    val unknown = Strings.errorUnknown
    return when (this) {
        HomeError.SessionNotFound    -> Strings.errorSessionNotFound
        HomeError.RemovedFromSession -> Strings.errorRemoved
        HomeError.SessionEnded       -> Strings.errorSessionEnded
        is HomeError.SessionFull     -> Strings.errorSessionFull(maxPlayers)
        is HomeError.CreateFailed    -> Strings.errorCreateFailed(detail ?: unknown)
        is HomeError.JoinFailed      -> Strings.errorJoinFailed(detail ?: unknown)
    }
}
