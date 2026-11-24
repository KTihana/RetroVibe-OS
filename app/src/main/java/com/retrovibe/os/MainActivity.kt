package com.retrovibe.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.ui.DesktopScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: OSViewModel = viewModel()
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopScreen(vm = vm)
            }
        }
    }
}
