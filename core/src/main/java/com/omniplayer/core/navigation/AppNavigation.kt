package com.omniplayer.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Explicit cross-feature route back to the app shell without importing its Activity. */
object AppNavigation {
    private const val HOME_ACTIVITY = "com.omniplayer.MainActivity"

    fun home(context: Context, tab: String): Intent = Intent().apply {
        component = ComponentName(context, HOME_ACTIVITY)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("open-tab", tab)
    }
}