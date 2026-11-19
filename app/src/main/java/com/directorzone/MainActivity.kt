package com.directorzone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.directorzone.ui.*

class MainActivity : ComponentActivity() {
    private val vm: EditorVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.directorzone.data.Prefs.init(applicationContext)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.getBooleanExtra("dztest", false) && DzTest.available(this)) {
            window.decorView.postDelayed({ DzTest.run(this, vm) }, 2500)
        }
        setContent {
            DirectorTheme {
                Box(Modifier.fillMaxSize().background(DZ.bg)) {
                    AnimatedContent(vm.inEditor, transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut() }, label = "screen") { editing ->
                        if (editing) EditorScreen(vm) else HomeScreen(vm)
                    }
                }
            }
        }
    }

    override fun onStop() { super.onStop(); if (vm.inEditor) vm.save() }
}
