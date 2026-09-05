package net.breadthcharge.exigentheron.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

/**
 * Phase 0: a blank screen. The permission-grant flow, rule list, and
 * settings screens arrive in later phases (see AGENTS.md §6, §3 ui/).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Scaffold { innerPadding ->
                        Text(
                            text = "exigent-heron",
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
