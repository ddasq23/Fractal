package dev.dfd.fractal

import androidx.compose.runtime.remember
import dev.dfd.fractal.common.ui.components.LocalGuiSettings
import dev.dfd.fractal.settings.AppearanceRoute
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
import dev.dfd.fractal.settings.settingsNavigation
import dev.dfd.fractal.titlemanager.TitleManagerRoute
import dev.dfd.fractal.titlemanager.titleManagerNavigation

class MainActivity : AppCompatActivity() {
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        GamepadInputSource.emitMotion(event)

        if (GamepadInputSource.hasMotionSubscribers) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        GamepadInputSource.emitKey(event)

        if (GamepadInputSource.hasKeySubscribers) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranslatableContent {
                ActivityContent {
                    MainNav()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NativeSettings.saveSettings()
    }

    override fun onPause() {
        super.onPause()
        NativeSettings.saveSettings()
    }
}

@Composable
private fun MainNav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    // ActivityContent provides the settings already loaded; first use starts on the welcome screen
    val gui = LocalGuiSettings.current
    val startDestination: Any = remember { if (gui?.onboardingCompleted == false) WelcomeRoute else GameListRoute }

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
            goToWelcome = { navController.navigate(WelcomeRoute) },
        )
        welcomeNavigation(navController)
        settingsNavigation(navController, goToWelcome = { navController.navigate(WelcomeRoute) })
        titleManagerNavigation(navController)
        graphicPacksNavigation(navController)
        aboutCemuNavigation(navController)
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
