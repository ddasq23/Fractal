package dev.dfd.fractal

import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import dev.dfd.fractal.games.library.tryShareDebugReport
import dev.dfd.fractal.games.library.DebugReport
import dev.dfd.fractal.games.library.CrashCheck
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import dev.dfd.fractal.common.android.context.DataFolder
import android.util.Log
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks
import dev.dfd.fractal.common.android.context.internalFolder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.sound.MenuSounds
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import dev.dfd.fractal.common.customdrivers.DriverGuard
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dfd.fractal.common.ui.localization.tr
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import dev.dfd.fractal.common.ui.intro.IntroAnimation
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import dev.dfd.fractal.common.ui.fractal.ButtonLegendHost
import dev.dfd.fractal.common.ui.fractal.GamepadUi
import dev.dfd.fractal.common.ui.intro.introAnimationsDisabled
import dev.dfd.fractal.common.ui.intro.introKeyFilter
import androidx.compose.runtime.remember
import dev.dfd.fractal.common.ui.components.LocalGuiSettings
import dev.dfd.fractal.settings.AppearanceRoute
import dev.dfd.fractal.settings.CustomDriversRoute
import dev.dfd.fractal.settings.GamePathsRoute
import dev.dfd.fractal.welcome.WelcomeRoute
import dev.dfd.fractal.welcome.welcomeNavigation
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.View
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.dfd.fractal.about.AboutCemuRoute
import dev.dfd.fractal.about.aboutCemuNavigation
import dev.dfd.fractal.common.input.GamepadInputSource
import dev.dfd.fractal.common.ui.components.ActivityContent
import dev.dfd.fractal.common.ui.localization.TranslatableContent
import dev.dfd.fractal.emulation.EmulationActivity
import dev.dfd.fractal.games.GameListRoute
import dev.dfd.fractal.games.gamesNavigation
import dev.dfd.fractal.graphicpacks.GraphicPacksRoute
import dev.dfd.fractal.graphicpacks.graphicPacksNavigation
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import dev.dfd.fractal.nativeinterface.NativeSettings
import dev.dfd.fractal.settings.SettingsRoute
import dev.dfd.fractal.settings.input.controller.AutoControllerMappingListener
import dev.dfd.fractal.settings.settingsNavigation
import dev.dfd.fractal.titlemanager.TitleManagerRoute
import dev.dfd.fractal.titlemanager.titleManagerNavigation

/**
 * settings.xml as this process last saved or read it. The game runs in its own process and saves what its menu
 * changes (graphics, graphic packs, sound...); without reading that back, this process's next save (on pause) would put
 * its older copy over it. Kept for the process, not the activity: coming back from a landscape game recreates the
 * library activity on a portrait screen.
 */
private var settingsSeen = 0L

class MainActivity : AppCompatActivity() {
    private val autoControllerMapping = AutoControllerMappingListener(this)

    private fun settingsFile() = File(internalFolder(), "settings.xml")

    private fun reloadSettingsChangedElsewhere() {
        val modified = settingsFile().lastModified()
        if (settingsSeen == 0L || modified == settingsSeen) {
            settingsSeen = modified
            return
        }
        settingsSeen = modified
        runCatching {
            NativeSettings.reloadSettings()
            // graphic packs keep their on/off state and presets from the settings when they're loaded
            NativeGraphicPacks.refreshGraphicPacks()
        }.onFailure { Log.w("MainActivity", "can't reload the settings", it) }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        GamepadUi.onMotion(event)
        GamepadInputSource.emitMotion(event)

        if (GamepadInputSource.hasMotionSubscribers) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) GamepadUi.onTouch()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        GamepadUi.onKey(event)
        GamepadInputSource.emitKey(event)

        if (GamepadInputSource.hasKeySubscribers) {
            return true
        }

        // In touch mode no view has focus, so a first gamepad button that is not a d-pad key
        // (Start, Y, X...) would never reach Compose. Leave touch mode and focus the content first.
        if (event.action == KeyEvent.ACTION_DOWN && currentFocus == null) {
            focusContentFromTouch()
        }

        return super.dispatchKeyEvent(event)
    }

    private fun focusContentFromTouch() {
        // ComposeView -> AndroidComposeView; addFocusables() is empty for Compose, so walk the first children
        var view: View? = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        while (view != null && !view.isFocusable) view = (view as? ViewGroup)?.getChildAt(0)
        view?.requestFocusFromTouch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            AppSettingsStore.dataStore.data.map { it.guiSettings }.distinctUntilChanged().collect { MenuSounds.apply(it) }
        }
        setContent {
            TranslatableContent {
                ActivityContent {
                    MainNav()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        GamepadUi.start(this)
        MenuSounds.onLibraryStarted()
    }

    override fun onStop() {
        super.onStop()
        GamepadUi.stop()
        MenuSounds.onLibraryStopped()
    }

    /** Saves this process's settings, after reading back what the game saved since (it would be lost otherwise). */
    private fun saveSettings() {
        if (DataFolder.switching) return
        reloadSettingsChangedElsewhere()
        NativeSettings.saveSettings()
        settingsSeen = settingsFile().lastModified()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
    }

    override fun onPause() {
        super.onPause()
        autoControllerMapping.unregister()
        saveSettings()
    }

    override fun onResume() {
        super.onResume()
        reloadSettingsChangedElsewhere()
        autoControllerMapping.register()
    }
}

@Composable
private fun MainNav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    // ActivityContent provides the settings already loaded; first use starts on the welcome screen
    val gui = LocalGuiSettings.current
    val startDestination: Any = remember { if (gui?.onboardingCompleted == false) WelcomeRoute else GameListRoute }
    // intro: on launch (if enabled and the setup is done) and, longer, right after the welcome setup
    var introPlaying by rememberSaveable {
        mutableStateOf(gui?.onboardingCompleted == true && gui.introOnLaunch && !introAnimationsDisabled())
    }
    var introLong by rememberSaveable { mutableStateOf(false) }
    val endIntro = { introPlaying = false }
    // a custom driver that failed in the last game: back on the system driver, tell the user
    var driverWarning by rememberSaveable { mutableStateOf<String?>(null) }
    // checked on every resume: in release builds the game runs in its own process, so a crash there
    // brings this activity back without restarting it
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    // a crash of the last game (or of the app): offer the debug report, unless the driver warning already explains it
    var crashOffer by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            DriverGuard.recover(context)?.let { driverWarning = it }
            val crashed = withContext(Dispatchers.IO) { CrashCheck.newCrash(context) } != null
            if (crashed && driverWarning == null) crashOffer = true
        }
    }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent(introKeyFilter({ introPlaying }, endIntro))) {
    ButtonLegendHost {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }) {
        gamesNavigation(
            navController = navController,
            startGame = { startGame(context, it) },
            tryCreateShortcut = { tryCreateShortcutForGame(context, it) },
            goToSettings = { navController.navigate(SettingsRoute) },
            goToTitleManager = { navController.navigate(TitleManagerRoute) },
            goToGraphicPacks = { navController.navigate(GraphicPacksRoute) },
            goToAboutCemu = { navController.navigate(AboutCemuRoute) },
            goToGamePaths = { navController.navigate(GamePathsRoute) },
            goToAppearance = { navController.navigate(AppearanceRoute) },
            goToDrivers = { navController.navigate(CustomDriversRoute) },
            goToWelcome = { navController.navigate(WelcomeRoute) },
        )
        welcomeNavigation(navController, onSetupFinished = {
            introLong = true
            introPlaying = true
        })
        settingsNavigation(navController, goToWelcome = { navController.navigate(WelcomeRoute) })
        titleManagerNavigation(navController)
        graphicPacksNavigation(navController)
        aboutCemuNavigation(navController)
    }
    }
    if (introPlaying) IntroAnimation(long = introLong, onFinished = endIntro)
    }
    if (crashOffer && !introPlaying && driverWarning == null) {
        FractalAlertDialog(
            onDismissRequest = { crashOffer = false },
            title = { Text(tr("Fractal closed unexpectedly")) },
            text = {
                Text(
                    tr("The last session ended with an error. A debug report (logs, device and settings, game session data; no saves or game files) helps find the cause: share it in an issue at github.com/dfdx047/Fractal/issues. It is also in Options › Share debug report.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    crashOffer = false
                    scope.launch {
                        val userData = File(NativeActiveSettings.getUserDataPath())
                        val report = withContext(Dispatchers.IO) { runCatching { DebugReport.build(userData) }.getOrNull() }
                        if (report == null || !tryShareDebugReport(context))
                            Toast.makeText(context, tr("Failed to create the debug report"), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(tr("Share report")) }
            },
            dismissButton = { TextButton(onClick = { crashOffer = false }) { Text(tr("Not now")) } },
        )
    }
    driverWarning?.takeIf { !introPlaying }?.let { text ->
        FractalAlertDialog(
            onDismissRequest = { driverWarning = null },
            title = { Text(tr("Custom driver failed")) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { driverWarning = null }) { Text(tr("OK")) } },
        )
    }
}

private fun createIntentForGame(context: Context, game: Game): Intent {
    val intent = Intent(
        context, EmulationActivity::class.java
    )
    intent.action = Intent.ACTION_VIEW
    intent.putExtra(EmulationActivity.EXTRA_LAUNCH_PATH, game.path)

    return intent
}

private fun startGame(context: Context, game: Game) {
    NativeSettings.saveSettings()

    val intent = createIntentForGame(context, game)
    context.startActivity(intent)
}

private fun tryCreateShortcutForGame(
    context: Context,
    game: Game,
): Boolean {
    try {
        val shortcutManager = context.getSystemService(
            ShortcutManager::class.java
        )
        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        val icon = game.icon?.asAndroidBitmap().let {
            if (it != null) Icon.createWithBitmap(it)
            else Icon.createWithResource(context, R.mipmap.ic_launcher)
        }

        val intent = createIntentForGame(context, game)

        val pinShortcutInfo =
            ShortcutInfo.Builder(context, game.titleId.toString()).setShortLabel(game.name!!)
                .setIntent(intent).setIcon(icon).build()

        val pinnedShortcutCallbackIntent =
            shortcutManager.createShortcutResultIntent(pinShortcutInfo)

        val successCallback = PendingIntent.getBroadcast(
            context, 0, pinnedShortcutCallbackIntent, PendingIntent.FLAG_IMMUTABLE
        )

        shortcutManager.requestPinShortcut(pinShortcutInfo, successCallback.intentSender)
        return true
    } catch (_: Exception) {
        return false
    }
}
