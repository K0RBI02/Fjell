package dev.korbi.helgeland

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens at the Fairphone 6's size and compares them with the reference
 * images in src/test/screenshots. Re-record after an intended change with
 * `./gradlew recordRoborazziDebug`; `verifyRoborazziDebug` (run in CI) fails on any other change.
 *
 * Android 15 rather than 16: Robolectric's Android 16 sandbox doesn't start on current JDKs yet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w372dp-h828dp-xxhdpi")
class ScreenshotTest {
    private val actions = FakeActions()

    /** Tiny anti-aliasing differences between machines shouldn't fail the build. */
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f))

    private fun capture(content: @Composable () -> Unit) = captureRoboImage(roborazziOptions = options) {
        CompositionLocalProvider(LocalClock provides Fixtures.clock) { content() }
    }

    @Test
    fun home() = capture {
        Launcher(
            apps = Fixtures.apps,
            gridKeys = Fixtures.grid,
            recentKeys = emptyList(),
            launchCounts = emptyMap(),
            hiddenKeys = emptySet(),
            weather = WeatherNow(7, Sky.Cloudy),
            nextAlarm = Fixtures.clock.millis() + 2 * 60 * 60 * 1000L,
            iconVersion = 0,
            globalIconStyle = IconStyle.Original,
            iconPacks = emptyList(),
            settings = LauncherSettings(media = true, showEvents = true),
            nowPlaying = NowPlaying("Burn It to the Ground", "Nickelback", playing = true, packageName = "app.spotify"),
            dotPackages = setOf("app.whatsapp"),
            noDots = emptySet(),
            homePresses = 0,
            actions = actions,
            events = listOf(CalendarEvent(1, "Dentist", Fixtures.clock.millis() + 50 * 60 * 1000L)),
        )
    }

    @Test
    fun stack() = capture {
        Drawer(
            apps = Fixtures.apps,
            gridKeys = Fixtures.grid,
            recentKeys = listOf(Fixtures.key("Discord"), Fixtures.key("Maps")),
            launchCounts = mapOf(Fixtures.key("Discord") to 5, Fixtures.key("Maps") to 3, Fixtures.key("YouTube") to 9),
            hiddenKeys = emptySet(),
            settings = LauncherSettings(),
            actions = actions,
            focusSearch = false,
            onClose = {},
        )
    }

    @Test
    fun settings() = capture {
        SettingsPopup(
            sampleApp = Fixtures.apps.first(),
            globalStyle = IconStyle.Original,
            iconPacks = emptyList(),
            settings = LauncherSettings(),
            stapelShown = true,
            searchShown = true,
            hiddenApps = emptyList(),
            actions = actions,
            onDismiss = {},
        )
    }
}
