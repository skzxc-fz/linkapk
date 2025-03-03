package com.slbalance

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.widget.RemoteViews
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody


class BalanceWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.slbalance.ACTION_REFRESH"
    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        context ?: return
        appWidgetManager ?: return

        appWidgetIds?.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)

        if (context != null && intent?.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                intent.component ?: return
            )

            appWidgetIds.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val balance = fetchBalance(context)
            withContext(Dispatchers.Main) {
                val views = RemoteViews(context.packageName, R.layout.widget_balance).apply {
                    setTextViewText(R.id.balanceTextView, balance)  // Set balance text

                    val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    setOnClickPendingIntent(R.id.refreshButton, pendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)  // Update the widget view
            }
        }
    }

    private fun fetchBalance(context: Context): String {
        val gfgPolicy =
            ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(gfgPolicy)
        val sharedPrefs = context.getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        if ((sharedPrefs.getString("imsi", null) == null) || (sharedPrefs.getString("token", null) == "")) {
            return "---"
        } else {
            val imsi = sharedPrefs.getString("imsi", null) ?: return "---"
            val token = sharedPrefs.getString("token", null) ?: return "---"
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val json = """
                {
                    "phone": "$imsi",
                    "token": "$token"
                }
            """.trimIndent()
            val request = okhttp3.Request.Builder()
                .url("https://silent.link/api/v1/checkbalance")
                .post(json.toRequestBody(mediaType))
                .build()
            val response = client.newCall(request).execute()
            val responseJson = Gson().fromJson(response.body?.string(), Map::class.java)
            val data = responseJson["data"] as? Map<*, *>
            val balance = data?.get("BALANCE") as? Double
            return "$$balance"
        }
    }
}