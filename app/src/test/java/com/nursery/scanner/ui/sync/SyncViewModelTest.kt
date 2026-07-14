package com.nursery.scanner.ui.sync

import com.nursery.core.DeviceConfig
import com.nursery.scanner.data.repo.CloudSyncActions
import com.nursery.scanner.data.repo.SyncResult
import com.nursery.scanner.data.repo.SyncState
import com.nursery.scanner.test.FakeSettingsConfigSource
import com.nursery.scanner.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun syncNowRunsCloudSyncAndSurfacesDoneMessage() = runTest {
        val sync = RecordingCloudSync(SyncResult.Done(salesCount = 2, cullCount = 1))
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig("07", "https://x/exec", "secret", 60))),
        )

        vm.syncNow()
        runCurrent()

        assertEquals(1, sync.calls)
        assertEquals(true, sync.lastForceFullPull)
        assertEquals("Synced (2 sales, 1 cull)", vm.message.value)
    }

    @Test
    fun syncNowSurfacesExportErrorFromCloudSync() = runTest {
        val sync = RecordingCloudSync(SyncResult.Error("sales push failed"))
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig("07", "https://x/exec", "secret", 60))),
        )

        vm.syncNow()
        runCurrent()

        assertEquals(1, sync.calls)
        assertEquals("Error: sales push failed", vm.message.value)
    }

    @Test
    fun syncNowKeepsPartialExportWarningWhenImportFails() = runTest {
        val sync = RecordingCloudSync(
            SyncResult.Error("plant pull failed", partialError = "Cull export failed"),
        )
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig("07", "https://x/exec", "secret", 60))),
        )

        vm.syncNow()
        runCurrent()

        assertEquals("Error: plant pull failed · Cull export failed", vm.message.value)
    }

    @Test
    fun syncNowSurfacesDoneWithPartialExportWarning() = runTest {
        val sync = RecordingCloudSync(
            SyncResult.Done(salesCount = 3, cullCount = 0, partialError = "Cull export failed"),
        )
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig("07", "https://x/exec", "secret", 60))),
        )

        vm.syncNow()
        runCurrent()

        assertEquals("Synced (3 sales) · Cull export failed", vm.message.value)
    }

    @Test
    fun syncNowIncludesLabelCountInDoneMessage() = runTest {
        val sync = RecordingCloudSync(
            SyncResult.Done(salesCount = 1, cullCount = 0, labelCount = 2),
        )
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig("07", "https://x/exec", "secret", 60))),
        )

        vm.syncNow()
        runCurrent()

        assertEquals("Synced (1 sales, 2 label)", vm.message.value)
    }

    @Test
    fun syncNowIncludesRepotCountInDoneMessage() = runTest {
        val sync = RecordingCloudSync(
            SyncResult.Done(salesCount = 0, cullCount = 0, labelCount = 0, repotCount = 2),
        )
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig("07", "https://x/exec", "secret", 60))),
        )

        vm.syncNow()
        runCurrent()

        assertEquals("Synced (2 repot)", vm.message.value)
    }

    @Test
    fun syncNowSurfacesNotConfiguredWithoutCallingFailurePath() = runTest {
        val sync = RecordingCloudSync(SyncResult.NotConfigured)
        val vm = SyncViewModel(
            sync,
            FakeSettingsConfigSource(MutableStateFlow(DeviceConfig.default())),
        )

        vm.syncNow()
        runCurrent()

        assertEquals(1, sync.calls)
        assertEquals("Set up the connection in Settings first", vm.message.value)
    }
}

private class RecordingCloudSync(
    private val result: SyncResult,
) : CloudSyncActions {
    var calls = 0
        private set

    override val state: StateFlow<SyncState> = MutableStateFlow(SyncState())

    override suspend fun syncCloud(forceFullPull: Boolean): SyncResult {
        calls++
        lastForceFullPull = forceFullPull
        return result
    }

    var lastForceFullPull: Boolean? = null
        private set
}
