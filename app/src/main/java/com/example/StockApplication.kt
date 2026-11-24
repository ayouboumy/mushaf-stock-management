package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

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
                    if (FirebaseApp.getApps(ctx).isEmpty()) {
                        FirebaseApp.initializeApp(ctx)
                        Log.i("StockApplication", "FirebaseApp initialized from google-services.json")
                    }
                    isInitialized = true
                } catch (e: Exception) {
                    Log.e("StockApplication", "Error initializing FirebaseApp", e)
                }
            }
        }
    }
}
