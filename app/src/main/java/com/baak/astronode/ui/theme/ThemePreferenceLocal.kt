package com.baak.astronode.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.baak.astronode.core.util.ThemePreference

val LocalThemePreference = compositionLocalOf<ThemePreference> {
    error("ThemePreference not provided")
}
