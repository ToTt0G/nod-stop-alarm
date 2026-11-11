package app.nod.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val PACKAGE = "app.nod"
private const val WAIT = 5_000L

/** Grants what onboarding asks for, so journeys run unattended. */
fun MacrobenchmarkScope.grantPermissions() {
    listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS").forEach {
        device.executeShellCommand("pm grant $PACKAGE android.permission.$it")
    }
    device.executeShellCommand("appops set $PACKAGE USE_FULL_SCREEN_INTENT allow")
}

/** Skips through onboarding if it's showing; returning users land straight on Home. */
fun MacrobenchmarkScope.passOnboarding() {
    device.findObject(By.text("Skip"))?.click() ?: return
    device.wait(Until.findObject(By.text("Get started")), WAIT)?.click()
    device.wait(Until.hasObject(By.text("Station, stop or city")), WAIT)
}

fun MacrobenchmarkScope.awaitHome() {
    device.wait(Until.hasObject(By.text("Station, stop or city")), WAIT)
}

fun MacrobenchmarkScope.searchAndOpenZone() {
    device.findObject(By.text("Station, stop or city"))?.click() ?: return
    device.waitForIdle()
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT) ?: return
    field.text = "Slussen"
    // Results come from the network; carry on regardless so the profile still covers the UI.
    device.wait(Until.findObject(By.text(Pattern.compile(".*Slussen.*"))), 8_000)
    device.findObjects(By.text(Pattern.compile(".*Slussen.*"))).lastOrNull()?.click()
    device.wait(Until.hasObject(By.text("Start trip")), WAIT)
    Thread.sleep(1_500) // let the map render a frame or two
    device.pressBack()
    device.waitForIdle()
    device.pressBack()
    awaitHome()
}

fun MacrobenchmarkScope.visitSettingsAndHistory() {
    device.findObject(By.desc("Settings"))?.let {
        it.click()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.fling(androidx.test.uiautomator.Direction.DOWN)
        device.waitForIdle()
        device.pressBack()
        awaitHome()
    }
    device.findObject(By.desc("History"))?.let {
        it.click()
        device.waitForIdle()
        device.pressBack()
        awaitHome()
    }
}
