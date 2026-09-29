package com.kregosh.mtglifetracker.data

interface UserPrefs {
    val userId      : String
    var displayName : String
    var backgroundImageUri      : String?
    var cardBackgroundImageUri  : String?
    var startLife               : UInt
    var commanderDeathThreshold : UInt
    var infectDeathThreshold    : UInt
    var colorScheme             : String
    var commanderDefaultEnabled : Boolean
}
