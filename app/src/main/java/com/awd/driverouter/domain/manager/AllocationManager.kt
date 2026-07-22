package com.awd.driverouter.domain.manager

import com.awd.driverouter.data.local.AccountDao
import com.awd.driverouter.data.local.AllocationStrategy
import com.awd.driverouter.data.local.SettingsManager
import com.awd.driverouter.domain.provider.CloudProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllocationManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val accountDao: AccountDao,
    private val providers: List<@JvmSuppressWildcards CloudProvider>
) {
    private var lastUsedIndex = -1
    private var weightedCounter = 0

    suspend fun getTargetAccountIds(customStrategy: AllocationStrategy? = null): List<String> {
        val strategy = customStrategy ?: settingsManager.strategy.value
        val accounts = accountDao.getAllAccounts().first()
        if (accounts.isEmpty()) return emptyList()

        return when (strategy) {
            AllocationStrategy.MIRROR -> accounts.map { it.id }
            
            AllocationStrategy.CUSTOM_ORDER -> {
                val order = settingsManager.customAccountOrder.value
                val sorted = accounts.sortedWith { a, b ->
                    val indexA = order.indexOf(a.id).let { if (it == -1) Int.MAX_VALUE else it }
                    val indexB = order.indexOf(b.id).let { if (it == -1) Int.MAX_VALUE else it }
                    indexA.compareTo(indexB)
                }
                listOf(sorted.first().id)
            }

            AllocationStrategy.WEIGHTED_ROUND_ROBIN -> {
                val candidates = mutableListOf<String>()
                accounts.forEach { account ->
                    val weight = (account.totalSpace / (1024 * 1024 * 1024)).toInt().coerceIn(1, 1000)
                    repeat(weight) { candidates.add(account.id) }
                }
                val selectedId = candidates[weightedCounter % candidates.size]
                weightedCounter++
                listOf(selectedId)
            }

            AllocationStrategy.MOST_FREE -> {
                val best = accounts.maxByOrNull { it.totalSpace - it.usedSpace } ?: accounts.first()
                listOf(best.id)
            }

            AllocationStrategy.LEAST_USED -> {
                // Percentage based ratio
                val best = accounts.minByOrNull { 
                    if (it.totalSpace > 0) it.usedSpace.toDouble() / it.totalSpace else 1.0 
                } ?: accounts.first()
                listOf(best.id)
            }

            AllocationStrategy.ROUND_ROBIN -> {
                lastUsedIndex = (lastUsedIndex + 1) % accounts.size
                listOf(accounts[lastUsedIndex].id)
            }

            AllocationStrategy.MANUAL -> listOf(accounts.first().id)
        }
    }

    suspend fun getBestProvider(customStrategy: AllocationStrategy? = null): CloudProvider? {
        val ids = getTargetAccountIds(customStrategy)
        if (ids.isEmpty()) return null
        val accountId = ids.first()
        val account = accountDao.getAllAccounts().first().find { it.id == accountId } ?: return null
        return providers.find { it.providerId == account.providerId }
    }
}
