package com.example.audioambientglow.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AodStateManager {
    private val _isAodActivityActive = MutableStateFlow(false)
    val isAodActivityActive = _isAodActivityActive.asStateFlow()

    fun setAodActive(active: Boolean) {
        _isAodActivityActive.value = active
    }
}
