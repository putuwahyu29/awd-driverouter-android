package com.awd.driverouter.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Transaction
    suspend fun setMainAccount(accountId: String) {
        clearMainAccount()
        setMainAccountInternal(accountId)
    }

    @Query("UPDATE accounts SET isMainAccount = 0")
    suspend fun clearMainAccount()

    @Query("UPDATE accounts SET isMainAccount = 1 WHERE id = :accountId")
    suspend fun setMainAccountInternal(accountId: String)

    @Query("SELECT * FROM accounts WHERE isMainAccount = 1 LIMIT 1")
    suspend fun getMainAccount(): AccountEntity?
}
