package com.directorzone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import com.directorzone.data.Prefs
import com.directorzone.ui.*

class MainActivity : ComponentActivity() {
    private val vm: EditorVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.getBooleanExtra("dztest", false) && DzTest.available(this)) {
            window.decorView.postDelayed({ DzTest.run(this, vm) }, 2500)
        }
        if (savedInstanceState == null && intent.getBooleanExtra("uitour", false) && UiTour.available(this)) {
            window.decorView.postDelayed({ UiTour.run(this, vm) }, 2500)
        }
        setContent {
            DirectorTheme {
                Box(Modifier.fillMaxSize().background(DZ.bg)) {
                    AnimatedContent(vm.inEditor, transitionSpec = { (fadeIn(tween(Motion.ms(260))) + slideInVertically(tween(Motion.ms(260))) { it / 14 }) togetherWith fadeOut(tween(Motion.ms(160))) }, label = "screen") { editing ->
                        if (editing) EditorScreen(vm) else HomeScreen(vm)
                    }
                    AnimatedContent(vm.showExport && vm.inEditor, transitionSpec = { (fadeIn(tween(Motion.ms(220))) + slideInVertically(tween(Motion.ms(260))) { it / 8 }) togetherWith fadeOut(tween(Motion.ms(160))) }, label = "export") { show ->
                        if (show) ExportScreen(vm) else Box(Modifier)
                    }
                    AnimatedContent(vm.showSettings, transitionSpec = { (fadeIn(tween(Motion.ms(220))) + slideInHorizontally(tween(Motion.ms(260))) { it / 6 }) togetherWith (fadeOut(tween(Motion.ms(160))) + slideOutHorizontally(tween(Motion.ms(200))) { it / 6 }) }, label = "settings") { show ->
                        if (show) SettingsScreen(vm) else Box(Modifier)
                    }
                    AnimatedContent(vm.showThemes, transitionSpec = { (fadeIn(tween(Motion.ms(220))) + slideInHorizontally(tween(Motion.ms(260))) { it / 6 }) togetherWith (fadeOut(tween(Motion.ms(160))) + slideOutHorizontally(tween(Motion.ms(200))) { it / 6 }) }, label = "themes") { show ->
                        if (show) ThemesScreen(vm) else Box(Modifier)
                    }
                }
            }
        }
    }

    override fun onStop() { super.onStop(); if (vm.inEditor) vm.save() }
}
