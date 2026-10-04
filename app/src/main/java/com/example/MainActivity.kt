package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppLayout
import com.example.ui.StockViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StockApplication.initializeFirebase(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val stockViewModel: StockViewModel = viewModel()
                AppLayout(viewModel = stockViewModel)
            }
        }
    }
}
