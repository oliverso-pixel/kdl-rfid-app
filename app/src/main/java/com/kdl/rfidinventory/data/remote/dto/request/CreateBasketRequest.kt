// data/remote/dto/request/CreateBasketRequest.kt (新增)
package com.kdl.rfidinventory.data.remote.dto.request

data class CreateBasketRequest(
    val rfid: String,
    val type: Int = 1, // 默認為 1
    val description: String = ""
)