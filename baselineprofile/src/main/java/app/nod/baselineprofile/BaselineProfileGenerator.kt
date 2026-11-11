package app.nod.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths of Nod's main journeys so they ship precompiled (AOT) instead of being
 * interpreted/JIT-compiled on first use. Run with:
 *   ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        grantPermissions()
        pressHome()
        startActivityAndWait()
        passOnboarding()
        awaitHome()
        searchAndOpenZone()
        visitSettingsAndHistory()
    }
}
