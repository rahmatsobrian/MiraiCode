package com.rahmatsobrian.miraicode

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    MaterialExpressiveTheme(colorScheme = cs, motionScheme = MotionScheme.expressive(), content = content)
}
