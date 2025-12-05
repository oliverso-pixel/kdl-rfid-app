package com.kdl.rfidinventory.util

sealed class NetworkState {
    data object Connected : NetworkState()
    data class Disconnected(val pendingCount: Int) : NetworkState()
    data object Unknown : NetworkState()
}