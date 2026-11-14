package com.qcbru.flashcards

import android.app.Application
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.qcbru.flashcards.storage.FlashDatabase

class FlashApplication : Application() {
    val database: FlashDatabase by lazy { Room.databaseBuilder(this, FlashDatabase::class.java, "flashcards.db").build() }
    val repository: FlashRepository by lazy { FlashRepository(this, database) }
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance() else PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
