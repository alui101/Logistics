package com.example.logistics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle

// EXISTING FUNCTION
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// --- NEW FUNCTION: SAFE NAVIGATION POP ---
fun NavController.safePopBackStack() {
    // 1. Check if we have a "Previous" screen to go back to.
    // 2. Check if the current screen is actually "Resumed" (visible).
    //    This prevents double-clicks from firing twice.
    val isResumed = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

    if (previousBackStackEntry != null && isResumed) {
        popBackStack()
    }
}