package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.repository.HostConfigRepository
import com.example.service.UbaidHostService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            val db = AppDatabase.getInstance(context)
            val repo = HostConfigRepository(db.hostConfigDao())

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val config = repo.getOrCreateConfig()
                    if (config.autoStartOnBoot) {
                        UbaidHostService.start(context)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
