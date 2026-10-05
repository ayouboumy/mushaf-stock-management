package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class StockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        initializeFirebase(this)
    }

    companion object {
        @Volatile
        var appContext: Context? = null
            private set

        @Volatile
        private var isInitialized = false

        fun initializeFirebase(context: Context?) {
            val ctx = context?.applicationContext ?: context ?: appContext ?: return
            appContext = ctx
            if (isInitialized && FirebaseApp.getApps(ctx).isNotEmpty()) return
            synchronized(this) {
                if (isInitialized && FirebaseApp.getApps(ctx).isNotEmpty()) return
                try {
                    val apps = FirebaseApp.getApps(ctx)
                    if (apps.isEmpty()) {
                        val options = FirebaseOptions.Builder()
                            .setApplicationId("1:853719870602:android:e77d76148604affc9dd173")
                            .setApiKey("AIzaSyCr1ToMuR2ejNwkRmnRcRU0zUZdcIfnUQM")
                            .setProjectId("mushaf-stock")
                            .setStorageBucket("mushaf-stock.firebasestorage.app")
                            .build()
                        FirebaseApp.initializeApp(ctx, options)
                        Log.i("StockApplication", "FirebaseApp initialized with explicit FirebaseOptions successfully")
                    }
                    isInitialized = true
                } catch (e: Exception) {
                    Log.e("StockApplication", "Error initializing FirebaseApp with options", e)
                }
            }
        }
    }
}
