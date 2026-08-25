package com.example.universaldesktopapp.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class DesktopMenuRequest(val x: Int, val y: Int, val id: Long = System.nanoTime())

object DesktopInputBus {
    private val mutableMenuRequests = MutableSharedFlow<DesktopMenuRequest>(extraBufferCapacity = 4)
    val menuRequests = mutableMenuRequests.asSharedFlow()
    fun showDesktopMenu(x: Int, y: Int) { mutableMenuRequests.tryEmit(DesktopMenuRequest(x, y)) }
}
