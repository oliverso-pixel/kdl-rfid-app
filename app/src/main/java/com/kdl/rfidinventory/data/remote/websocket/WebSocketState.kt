// data/remote/websocket/WebSocketState.kt
package com.kdl.rfidinventory.data.remote.websocket

sealed class WebSocketState {
    object Connecting : WebSocketState()
    object Connected : WebSocketState()
    data class Disconnected(val reason: String = "") : WebSocketState()
    data class Error(val error: String) : WebSocketState()
}