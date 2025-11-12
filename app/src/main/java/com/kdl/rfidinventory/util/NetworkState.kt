package com.kdl.rfidinventory.util

sealed class NetworkState {
    data object Connected : NetworkState()
    data class Offline(val pendingCount: Int) : NetworkState()
    data object ConnectionFailed : NetworkState()
}