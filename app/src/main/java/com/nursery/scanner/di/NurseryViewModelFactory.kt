package com.nursery.scanner.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nursery.scanner.ui.cull.CullViewModel
import com.nursery.scanner.ui.culls.CullListViewModel
import com.nursery.scanner.ui.plants.PlantListViewModel
import com.nursery.scanner.ui.printlabel.LabelPrintViewModel
import com.nursery.scanner.ui.receipts.ReceiptsViewModel
import com.nursery.scanner.ui.sell.SellViewModel
import com.nursery.scanner.ui.settings.SettingsViewModel
import com.nursery.scanner.ui.sync.SyncViewModel

class NurseryViewModelFactory(private val c: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(SellViewModel::class.java) ->
                SellViewModel(c.plantRepository, c.receiptRepository, c.settingsRepository)
            modelClass.isAssignableFrom(CullViewModel::class.java) ->
                CullViewModel(c.plantRepository, c.cullRepository, c.settingsRepository)
            modelClass.isAssignableFrom(LabelPrintViewModel::class.java) ->
                LabelPrintViewModel(c.plantRepository, c.labelPrintRepository, c.settingsRepository)
            modelClass.isAssignableFrom(ReceiptsViewModel::class.java) ->
                ReceiptsViewModel(c.receiptRepository)
            modelClass.isAssignableFrom(SyncViewModel::class.java) ->
                SyncViewModel(c.syncRepository, c.settingsRepository)
            modelClass.isAssignableFrom(PlantListViewModel::class.java) ->
                PlantListViewModel(c.plantRepository)
            modelClass.isAssignableFrom(CullListViewModel::class.java) ->
                CullListViewModel(c.cullRepository)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(c.settingsRepository)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
