package com.nursery.scanner.test

import com.nursery.core.DeviceConfig
import com.nursery.scanner.data.settings.SettingsConfigSource
import kotlinx.coroutines.flow.Flow

class FakeSettingsConfigSource(
    override val config: Flow<DeviceConfig>,
) : SettingsConfigSource
