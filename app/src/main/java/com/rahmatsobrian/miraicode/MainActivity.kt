package com.rahmatsobrian.miraicode

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { java.io.File(filesDir, "crash.txt").writeText(e.stackTraceToString()) }
            prev?.uncaughtException(t, e)
        }
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(clear, clear),
            navigationBarStyle = SystemBarStyle.auto(clear, clear)   // tanpa scrim abu-abu di bar navigasi 3 tombol
        )
        setContent {
            val vm: MainVM = viewModel()
            MiraiTheme(vm) { MiraiApp(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiraiTheme(vm: MainVM, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (vm.theme) { 0 -> isSystemInDarkTheme(); 1 -> false; else -> true }
    val dyn = vm.dynamic && Build.VERSION.SDK_INT >= 31   // Android 9-11: fallback skema expressive
    var cs = when {
        dyn && dark -> dynamicDarkColorScheme(ctx)
        dyn -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    if (vm.theme == 3) cs = cs.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF0F0F0F),
        surfaceContainerHigh = Color(0xFF161616), surfaceContainerHighest = Color(0xFF1E1E1E)
    )
    val view = LocalView.current
    SideEffect {   // ikon status/nav bar mengikuti tema aplikasi, bukan tema sistem
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
    }
    MaterialExpressiveTheme(colorScheme = cs, motionScheme = MotionScheme.expressive(), content = content)
}
