package com.athletebridge.android.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.athletebridge.android.MainActivity

fun setupAppShortcuts(context: Context) {
    val shortcuts = listOf(
        ShortcutInfoCompat.Builder(context, "shortcut_messages")
            .setShortLabel("Messages")
            .setLongLabel("Open Messages")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_dialog_email))
            .setIntent(Intent(context, MainActivity::class.java).apply {
                action = "com.athletebridge.android.ACTION_MESSAGES"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            .build(),
        ShortcutInfoCompat.Builder(context, "shortcut_bookings")
            .setShortLabel("Bookings")
            .setLongLabel("View My Bookings")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_my_calendar))
            .setIntent(Intent(context, MainActivity::class.java).apply {
                action = "com.athletebridge.android.ACTION_BOOKINGS"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            .build()
    )
    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
}
