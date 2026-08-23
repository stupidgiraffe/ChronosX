package dev.chronosx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chronosx.ui.ChronosApp
import dev.chronosx.ui.ChronosViewModel
import dev.chronosx.ui.ChronosViewModelFactory
import dev.chronosx.ui.theme.ChronosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronosTheme {
                val model: ChronosViewModel = viewModel(
                    factory = ChronosViewModelFactory((application as ChronosXApplication).container),
                )
                ChronosApp(model)
            }
        }
    }
}
