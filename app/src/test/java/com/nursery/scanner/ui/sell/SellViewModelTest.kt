package com.nursery.scanner.ui.sell

import com.nursery.core.DeviceConfig
import com.nursery.core.LineItem
import com.nursery.core.PaymentMethod
import com.nursery.core.PlantBook
import com.nursery.core.Receipt
import com.nursery.core.ReceiptStatus
import com.nursery.core.SaleUnit
import com.nursery.scanner.data.repo.PlantBookSource
import com.nursery.scanner.data.repo.ReceiptSaver
import com.nursery.scanner.test.FakeSettingsConfigSource
import com.nursery.scanner.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun finishAndSavePersistsTappedPaymentMethodWhenSelectionChangesBeforeConfigLoads() = runTest {
        val config = MutableSharedFlow<DeviceConfig>()
        val receiptSaver = RecordingReceiptSaver()
        val viewModel = SellViewModel(
            plantRepo = FakePlantBookSource(),
            receiptRepo = receiptSaver,
            settings = FakeSettingsConfigSource(config),
        )

        viewModel.onCode("404")
        viewModel.sellAsUnknown()
        viewModel.commitDraft(qty = 1, unitPriceCents = 500, discountPct = 0, unit = SaleUnit.POTS)
        viewModel.setPaymentMethod(PaymentMethod.CARD)

        viewModel.finishAndSave()
        runCurrent()
        assertTrue(viewModel.ui.value.isSaving)

        viewModel.setPaymentMethod(PaymentMethod.CASH)
        assertEquals(PaymentMethod.CARD, viewModel.ui.value.paymentMethod)

        config.emit(DeviceConfig.default())
        runCurrent()

        assertEquals(PaymentMethod.CARD, receiptSaver.savedPaymentMethod)
        assertEquals(PaymentMethod.CARD, viewModel.ui.value.saved?.paymentMethod)
        assertFalse(viewModel.ui.value.isSaving)
    }
}

private class FakePlantBookSource : PlantBookSource {
    override val plantBook: Flow<PlantBook> = MutableStateFlow(PlantBook(emptyList()))
}

private class RecordingReceiptSaver : ReceiptSaver {
    var savedPaymentMethod: PaymentMethod? = null

    override suspend fun saveReceipt(
        lines: List<LineItem>,
        config: DeviceConfig,
        paymentMethod: PaymentMethod,
    ): Receipt {
        savedPaymentMethod = paymentMethod
        return Receipt(
            localId = 1,
            receiptNo = "00-1-1",
            createdAtEpochMs = 0,
            status = ReceiptStatus.SAVED,
            lines = lines.mapIndexed { index, line -> line.copy(itemSeq = index + 1) },
            paymentMethod = paymentMethod,
        )
    }
}
