package net.wasms.smsgateway.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.wasms.smsgateway.data.local.db.WaSmsDatabase
import net.wasms.smsgateway.data.local.db.dao.DeliveryReportDao
import net.wasms.smsgateway.data.local.db.dao.DeviceConfigDao
import net.wasms.smsgateway.data.local.db.dao.SimCardDao
import net.wasms.smsgateway.data.local.db.dao.SmsMessageDao
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val KEYSTORE_ALIAS = "wasms_db_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "wasms_db_prefs"
    private const val PREFS_KEY_PASSPHRASE = "db_passphrase_encrypted"
    private const val PREFS_KEY_IV = "db_passphrase_iv"

    @Provides
    @Singleton
    fun provideSupportOpenHelperFactory(@ApplicationContext context: Context): SupportOpenHelperFactory {
        val passphrase = getOrCreatePassphrase(context)
        return SupportOpenHelperFactory(passphrase)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        supportFactory: SupportOpenHelperFactory
    ): WaSmsDatabase {
        return Room.databaseBuilder(
            context,
            WaSmsDatabase::class.java,
            WaSmsDatabase.DATABASE_NAME
        )
            .openHelperFactory(supportFactory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSmsMessageDao(database: WaSmsDatabase): SmsMessageDao {
        return database.smsMessageDao()
    }

    @Provides
    fun provideSimCardDao(database: WaSmsDatabase): SimCardDao {
        return database.simCardDao()
    }

    @Provides
    fun provideDeliveryReportDao(database: WaSmsDatabase): DeliveryReportDao {
        return database.deliveryReportDao()
    }

    @Provides
    fun provideDeviceConfigDao(database: WaSmsDatabase): DeviceConfigDao {
        return database.deviceConfigDao()
    }

    /**
     * Retrieves or generates a database encryption passphrase.
     *
     * The passphrase is generated once, encrypted with an Android Keystore-backed AES key,
     * and stored in SharedPreferences. On subsequent calls, the encrypted passphrase is
     * decrypted using the Keystore key and returned.
     *
     * This ensures:
     * - The passphrase never exists in plaintext on disk
     * - The Keystore key is hardware-backed where available
     * - The passphrase survives app restarts but not Keystore wipes
     */
    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEncrypted = prefs.getString(PREFS_KEY_PASSPHRASE, null)
        val existingIv = prefs.getString(PREFS_KEY_IV, null)

        return if (existingEncrypted != null && existingIv != null) {
            // Decrypt existing passphrase
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = android.util.Base64.decode(existingIv, android.util.Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
            val encryptedBytes = android.util.Base64.decode(existingEncrypted, android.util.Base64.NO_WRAP)
            cipher.doFinal(encryptedBytes)
        } else {
            // Generate new passphrase (32 random bytes)
            val passphrase = ByteArray(32)
            java.security.SecureRandom().nextBytes(passphrase)

            // Encrypt with Keystore key
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(passphrase)
            val iv = cipher.iv

            // Store encrypted passphrase and IV
            prefs.edit()
                .putString(PREFS_KEY_PASSPHRASE, android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP))
                .putString(PREFS_KEY_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .apply()

            passphrase
        }
    }

    /**
     * Retrieves or creates an AES-256 key in the Android Keystore.
     * The key is hardware-backed on devices that support it.
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        keyStore.getKey(KEYSTORE_ALIAS, null)?.let {
            return it as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
