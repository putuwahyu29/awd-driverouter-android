package com.awd.driverouter.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.driverouter.domain.model.Transfer
import com.awd.driverouter.domain.model.TransferStatus
import com.awd.driverouter.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val transferRepository: TransferRepository
) : ViewModel() {

    val activeTransfers: StateFlow<List<Transfer>> = transferRepository.getAllTransfers()
        .map { transfers ->
            transfers.filter { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PENDING }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalProgress: StateFlow<Float> = activeTransfers.map { transfers ->
        if (transfers.isEmpty()) 0f
        else transfers.sumOf { it.progress.toDouble() }.toFloat() / transfers.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
}
