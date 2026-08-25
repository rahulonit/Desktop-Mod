package com.example.universaldesktopapp.ui.apps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class MediaKind { IMAGE, VIDEO, AUDIO }
data class MediaOpenRequest(val id: Long = System.nanoTime(), val file: File, val kind: MediaKind)

object MediaOpenController {
    private val mutableRequest = MutableStateFlow<MediaOpenRequest?>(null)
    val request = mutableRequest.asStateFlow()
    fun open(file: File, kind: MediaKind) { mutableRequest.value = MediaOpenRequest(file = file, kind = kind) }
    fun consumed(request: MediaOpenRequest) { if (mutableRequest.value?.id == request.id) mutableRequest.value = null }
}
