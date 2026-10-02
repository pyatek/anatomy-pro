package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }
}
