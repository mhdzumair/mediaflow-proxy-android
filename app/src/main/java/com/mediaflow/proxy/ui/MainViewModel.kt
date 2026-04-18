package com.mediaflow.proxy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.proxy.ConfigRepository
import com.mediaflow.proxy.ProxyConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)
    val config = repo.config

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun setRunning(running: Boolean) { _isRunning.value = running }
    fun appendLog(line: String) { _logs.value = (_logs.value + line).takeLast(500) }
    fun clearLogs() { _logs.value = emptyList() }
    fun saveConfig(cfg: ProxyConfig) { viewModelScope.launch { repo.save(cfg) } }
}
