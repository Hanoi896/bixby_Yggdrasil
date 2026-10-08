package com.huginmunin.data.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.huginmunin.core.crypto.CryptoManager
import com.huginmunin.data.dao.LogDao
import com.huginmunin.data.dao.PermissionDao
import com.huginmunin.data.dao.TrusteeDao
import com.huginmunin.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import android.util.Base64
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_NAME = "hugin_munin.db"
    private const val PREFS_NAME = "db_security_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_encrypted"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): AppDatabase {
        val passphrase = getOrGeneratePassphrase(context, cryptoManager)
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // For MVP simplicity
            .build()
    }

    private fun getOrGeneratePassphrase(context: Context, cryptoManager: CryptoManager): ByteArray {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphrase = prefs.getString(KEY_DB_PASSPHRASE, null)

        return if (encryptedPassphrase != null) {
            val encryptedBytes = Base64.decode(encryptedPassphrase, Base64.DEFAULT)
            cryptoManager.decrypt(encryptedBytes)
        } else {
            val random = SecureRandom()
            val newPassphrase = ByteArray(32)
            random.nextBytes(newPassphrase)

            val encryptedBytes = cryptoManager.encrypt(newPassphrase)
            val encryptedString = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)

            prefs.edit().putString(KEY_DB_PASSPHRASE, encryptedString).apply()
            newPassphrase
        }
    }

    @Provides
    fun provideLogDao(database: AppDatabase): LogDao = database.logDao()

    @Provides
    fun providePermissionDao(database: AppDatabase): PermissionDao = database.permissionDao()

    @Provides
    fun provideTrusteeDao(database: AppDatabase): TrusteeDao = database.trusteeDao()
}
