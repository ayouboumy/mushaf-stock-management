package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class StockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase(this)
    }

    companion object {
        @Volatile
        private var isInitialized = false

        fun initializeFirebase(context: Context) {
            val appContext = context.applicationContext ?: context
            if (isInitialized && FirebaseApp.getApps(appContext).isNotEmpty()) return
            synchronized(this) {
                if (isInitialized && FirebaseApp.getApps(appContext).isNotEmpty()) return
                try {
                    val apps = FirebaseApp.getApps(appContext)
                    if (apps.isEmpty()) {
                        try {
                            FirebaseApp.initializeApp(appContext)
                            Log.i("StockApplication", "FirebaseApp initialized via default provider/resources")
                        } catch (e: Exception) {
                            Log.w("StockApplication", "Default initializeApp failed, using programmatic FirebaseOptions fallback", e)
                            val options = FirebaseOptions.Builder()
                                .setApplicationId("1:853719870602:android:e77d76148604affc9dd173")
                                .setApiKey("AIzaSyCr1ToMuR2ejNwkRmnRcRU0zUZdcIfnUQM")
                                .setProjectId("mushaf-stock")
                                .setStorageBucket("mushaf-stock.firebasestorage.app")
                                .build()
                            FirebaseApp.initializeApp(appContext, options)
                            Log.i("StockApplication", "FirebaseApp initialized via explicit FirebaseOptions")
                        }
                    }
                    isInitialized = true
                } catch (e: Exception) {
                    Log.e("StockApplication", "Error initializing FirebaseApp", e)
                }
            }
        }
    }
}
