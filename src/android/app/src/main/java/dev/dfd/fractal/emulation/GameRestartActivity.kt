package dev.dfd.fractal.emulation

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.IntentCompat
import dev.dfd.fractal.MainActivity
import kotlin.system.exitProcess

/**
 * Starts a game again in a fresh process. The emulator can't boot a second title in the process that ran the first, so
 * the running one asks this activity (in a process of its own) to end it and open the library with the game on top.
 */
class GameRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = IntentCompat.getParcelableExtra(intent, EXTRA_GAME_INTENT, Intent::class.java)
        Process.killProcess(intent.getIntExtra(EXTRA_PID, -1))
        if (intent.getBooleanExtra(EXTRA_WHOLE_APP, false)) {
            // every other process of the app (the game runs in one of its own) reads its settings again at start
            getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.filter { it.pid != Process.myPid() }
                ?.forEach { Process.killProcess(it.pid) }
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } else if (game != null) {
            val library = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivities(arrayOf(library, game))
        }
        finish()
        exitProcess(0)
    }

    companion object {
        private const val EXTRA_PID = "pid"
        private const val EXTRA_GAME_INTENT = "game"
        private const val EXTRA_WHOLE_APP = "wholeApp"

        /** Ends every process of the app and opens the library in a new one (after the data folder changed). */
        fun restartApp(context: Context) {
            context.startActivity(
                Intent(context, GameRestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_PID, Process.myPid())
                    .putExtra(EXTRA_WHOLE_APP, true),
            )
        }

        /** Ends this process and starts [gameIntent] in a new one. */
        fun restart(context: Context, gameIntent: Intent) {
            context.startActivity(
                Intent(context, GameRestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_PID, Process.myPid())
                    .putExtra(EXTRA_GAME_INTENT, Intent(gameIntent).setFlags(0)),
            )
        }
    }
}
