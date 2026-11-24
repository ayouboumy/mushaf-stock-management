package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.db.AppDatabase
import com.example.data.repository.StockRepository
import com.example.data.sync.authStateFlow
import com.example.ui.AppLayout
import com.example.ui.StockViewModel
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.screens.SignUpOnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StockApplication.initializeFirebase(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authStateFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authStateFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { /* authStateFlow emits the signed-in FirebaseUser */ },
                onUnauthenticated = { /* User stays on SignUpOnboardingScreen to tap Google Sign-In */ },
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        val prefs = remember(context) {
            context.applicationContext.getSharedPreferences("mushaf_stock_prefs", Context.MODE_PRIVATE)
        }
        SignUpOnboardingScreen(
            onAuthSuccess = { /* authStateFlow updates automatically */ },
            onSavePendingProfile = { fullName, role, phone, adminCode ->
                prefs.edit()
                    .putString("pending_full_name", fullName)
                    .putString("pending_job_title", role)
                    .putString("pending_phone", phone)
                    .putString("pending_admin_code", adminCode)
                    .apply()
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            val stockViewModel: StockViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = AppDatabase.getDatabase(app)
                        val repo = StockRepository(app, db)
                        val prefs = app.getSharedPreferences("mushaf_stock_prefs", Context.MODE_PRIVATE)
                        StockViewModel(
                            repository = repo,
                            database = db,
                            prefs = prefs,
                            currentUserId = uid
                        )
                    }
                }
            )
            AppLayout(viewModel = stockViewModel)
        }
    }
}
