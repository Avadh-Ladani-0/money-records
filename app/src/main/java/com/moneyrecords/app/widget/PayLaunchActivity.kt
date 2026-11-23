package com.moneyrecords.app.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.moneyrecords.app.MainActivity
import com.moneyrecords.app.detect.DetectionPipeline
import com.moneyrecords.app.detect.ScanRoutes
import com.moneyrecords.app.notify.Notifications
import com.moneyrecords.app.work.PayReminderWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Invisible step between a pay widget and the payment app: remembers "a payment started", opens the
 * app (on its scanner when possible) from this task — so Back after paying returns here — and then
 * opens the record editor for it. If you never come back, a reminder follows a few minutes later.
 */
@AndroidEntryPoint
class PayLaunchActivity : ComponentActivity() {
    @Inject lateinit var pipeline: DetectionPipeline
    @Inject lateinit var watcher: com.moneyrecords.app.detect.PayWatcher
    private var detectedId = 0L
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detectedId = savedInstanceState?.getLong(STATE_ID) ?: 0L
        launched = savedInstanceState?.getBoolean(STATE_LAUNCHED) ?: false
        if (launched) return
        val pkg = intent.getStringExtra(EXTRA_APP)
        if (pkg == null) {
            // Not set up yet: open the widget's setup in the app.
            startActivity(Intent(this, MainActivity::class.java).setAction(Notifications.ACTION_OPEN_AUTO_DETECT))
            finish()
            return
        }
        lifecycleScope.launch {
            detectedId = pipeline.startPayment(pkg).id
            WorkManager.getInstance(this@PayLaunchActivity).enqueue(
                OneTimeWorkRequestBuilder<PayReminderWorker>()
                    .setInitialDelay(3, TimeUnit.MINUTES)
                    .setInputData(workDataOf(Notifications.EXTRA_DETECTED_ID to detectedId))
                    .build(),
            )
            val target = ScanRoutes.intentFor(this@PayLaunchActivity, pkg)
            if (target == null) { finish(); return@launch }
            launched = true
            runCatching { startActivity(target) }.onFailure { finish() }
            // Left by swiping up instead of Back: ask straight away.
            watcher.watch(pkg, detectedId)
        }
    }

    /** Back from the payment app: record it now. */
    override fun onRestart() {
        super.onRestart()
        if (!launched || detectedId == 0L) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Notifications.ACTION_ADD_DETECTED)
                .putExtra(Notifications.EXTRA_DETECTED_ID, detectedId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_ID, detectedId)
        outState.putBoolean(STATE_LAUNCHED, launched)
    }

    companion object {
        const val EXTRA_APP = "app"
        const val EXTRA_ACCOUNT = "account"
        private const val STATE_ID = "id"
        private const val STATE_LAUNCHED = "launched"
    }
}
