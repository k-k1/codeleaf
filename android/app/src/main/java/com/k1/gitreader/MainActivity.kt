package com.k1.gitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.k1.gitreader.ui.GitReaderApp
import com.k1.gitreader.ui.GitReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GitReaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitReaderApp()
                }
            }
        }
    }
}
