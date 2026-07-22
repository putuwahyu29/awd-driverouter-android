package com.awd.driverouter.ui.screens.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.driverouter.data.local.AccountDao
import com.awd.driverouter.data.local.AllocationStrategy
import com.awd.driverouter.data.local.SettingsManager
import com.awd.driverouter.data.local.toDomain
import com.awd.driverouter.domain.model.CloudAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class StrategyViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val accountDao: AccountDao
) : ViewModel() {

    val strategy: StateFlow<AllocationStrategy> = settingsManager.strategy
    
    val accounts: StateFlow<List<CloudAccount>> = accountDao.getAllAccounts()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _orderedAccounts = MutableStateFlow<List<CloudAccount>>(emptyList())
    val orderedAccounts: StateFlow<List<CloudAccount>> = combine(accounts, settingsManager.customAccountOrder) { accs, order ->
        if (order.isEmpty()) accs
        else {
            accs.sortedWith { a, b ->
                val indexA = order.indexOf(a.id).let { if (it == -1) Int.MAX_VALUE else it }
                val indexB = order.indexOf(b.id).let { if (it == -1) Int.MAX_VALUE else it }
                indexA.compareTo(indexB)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStrategy(strategy: AllocationStrategy) {
        settingsManager.setStrategy(strategy)
    }

    fun updateOrder(newOrder: List<CloudAccount>) {
        settingsManager.setCustomAccountOrder(newOrder.map { it.id })
    }
}
