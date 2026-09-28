package com.example.categorydockwidget.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.categorydockwidget.widget.CategoryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AppRepository.reloadApps(context)
                        CategoryWidgetProvider.updateAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
