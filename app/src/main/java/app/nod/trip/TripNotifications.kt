package app.nod.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.nod.data.model.TripPlan
import app.nod.MainActivity
import app.nod.R
import app.nod.alarm.AlarmActivity
import app.nod.core.Format
import app.nod.data.model.TriggerMode
import app.nod.data.model.Units

object TripNotifications {
    /**
     * The live trip. Default importance (but silent): low-importance channels count as "silent"
     * notifications, which many lock screens hide, and the Live Update belongs on the lock screen.
     */
    const val CH_TRIP = "trip_live"
    private const val CH_TRIP_OLD = "trip"
    const val CH_HEADS_UP = "heads_up"
    const val CH_ALARM = "alarm"
    const val CH_DONE = "done"

    const val ID_TRIP = 1
    const val ID_ALARM = 2
    const val ID_HEADS_UP = 3
    const val ID_SUMMARY = 4

    const val EXTRA_OPEN_TRIP = "open_trip"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(CH_TRIP_OLD)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TRIP, context.getString(R.string.channel_trip), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_trip_desc)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CH_HEADS_UP, context.getString(R.string.channel_heads_up), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_heads_up_desc)
                    setSound(null, null)
                    enableVibration(false) // Nod vibrates itself so the pattern is consistent.
                },
                NotificationChannel(CH_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_alarm_desc)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
                NotificationChannel(CH_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_done_desc)
                    setSound(null, null)
                    setShowBadge(false)
                },
            ),
        )
    }

    /** Whether the trip can show as a Live Update (lock screen, AOD, status bar chip) on Android 16+. */
    fun canPromote(context: Context): Boolean =
        Build.VERSION.SDK_INT < 36 || context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

    fun openLiveUpdateSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 10,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_TRIP, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun service(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getService(
        context, code,
        Intent(context, TripService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun alarmScreen(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 11,
        Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Journey bar colours: the ride in calm blue, the final approach in Nod lime.
    private const val RIDE = 0xFF7CC4FF.toInt()
    private const val APPROACH = 0xFF8CC63F.toInt()
    private const val HEADS_UP = 0xFFFFB547.toInt()
    private const val ARRIVE = 0xFF5E8C00.toInt()
    private const val MAX = 1000

    /**
     * Everything the trip notification shows, quantised to what a person can perceive. Two equal
     * values render identically, so the service can skip the repost (and System UI's re-render).
     */
    data class TripContent(
        val title: String,
        val text: String,
        val chip: String,
        /** 0..MAX, or null while locating. Rounded to half a percent. */
        val progress: Int?,
        val headsUpMark: Int,
        /** Wall-clock target of the countdown chronometer, rounded to 15 s; null when not counting. */
        val countdownAt: Long?,
    )

    fun content(context: Context, state: TripState.Active, units: Units): TripContent {
        val plan = state.plan
        val dest = plan.destination
        val live = state.live ?: return TripContent(
            title = context.getString(R.string.notif_heading_to, dest.name),
            text = context.getString(R.string.notif_locating),
            chip = context.getString(R.string.chip_locating),
            progress = null, headsUpMark = 0, countdownAt = null,
        )
        val dist = Format.distance(live.distanceM, units).toString()
        val alarmIn = live.alarmInSec
        val text = when {
            state.phase == TripPhase.ALARM -> context.getString(R.string.notif_arrived)
            live.toZoneM <= 0.0 -> context.getString(R.string.notif_in_zone)
            alarmIn != null -> context.getString(R.string.notif_alarm_in, Format.duration(alarmIn)) +
                " · " + Format.speed(live.speedMps, units)
            plan.triggerMode == TriggerMode.TIME -> context.getString(R.string.notif_waiting_time, plan.minutesBefore)
            else -> context.getString(R.string.notif_waiting_zone)
        }
        // Where the 2-minute heads-up lands on the bar, based on current speed.
        val headsUpFrac = if (live.travelSpeedMps >= TripEngine.MOVING_MPS && live.startToZoneM > 0) {
            (1.0 - TripEngine.HEADS_UP_SEC * live.travelSpeedMps / live.startToZoneM).coerceIn(0.5, 0.95)
        } else 0.85
        val countdown = if (alarmIn != null && alarmIn > 0 && state.phase != TripPhase.ALARM) {
            val at = System.currentTimeMillis() + (alarmIn * 1000).toLong()
            (at + 7_500) / 15_000 * 15_000
        } else null
        return TripContent(
            title = context.getString(R.string.notif_distance_to, dist, dest.name),
            text = text,
            chip = if (alarmIn != null && alarmIn >= 60) Format.duration(alarmIn) else dist,
            progress = ((live.progress * MAX).toInt().coerceIn(0, MAX) + 2) / 5 * 5,
            headsUpMark = ((headsUpFrac * MAX).toInt() + 5) / 10 * 10,
            countdownAt = countdown,
        )
    }

    /**
     * The ongoing trip as an Android 16 Live Update: a segmented journey bar with a vehicle tracker,
     * a heads-up marker, a status-bar chip and a ticking countdown. Degrades to a classic progress
     * notification on older Android versions.
     */
    fun trip(
        context: Context,
        state: TripState.Active,
        units: Units,
        content: TripContent = content(context, state, units),
    ): Notification {
        val dest = state.plan.destination
        val kindIcon = IconCompat.createWithResource(context, dest.kind.iconRes())
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(false)
            // No start icon: at 0% it would sit under the tracker and show the vehicle twice.
            .setProgressTrackerIcon(kindIcon)
            .setProgressEndIcon(IconCompat.createWithResource(context, R.drawable.ic_moon))

        val b = NotificationCompat.Builder(context, CH_TRIP)
            .setSmallIcon(R.drawable.ic_stat_nod)
            .setColor(ContextCompat.getColor(context, R.color.nod_lime_deep))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
            .setSubText(context.getString(dest.kind.labelRes()))
            .addAction(R.drawable.ic_moon, context.getString(R.string.action_end_trip), service(context, TripService.ACTION_STOP, 20))
            .setRequestPromotedOngoing(true)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setShortCriticalText(content.chip)

        if (content.progress == null) {
            style.setProgressIndeterminate(true)
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(MAX).setColor(RIDE)))
        } else {
            val hu = content.headsUpMark
            style.setProgress(content.progress)
                .setProgressSegments(
                    listOf(
                        NotificationCompat.ProgressStyle.Segment(hu).setColor(RIDE),
                        NotificationCompat.ProgressStyle.Segment(MAX - hu).setColor(APPROACH),
                    ),
                )
                .setProgressPoints(
                    listOf(
                        NotificationCompat.ProgressStyle.Point(hu).setColor(HEADS_UP),
                        NotificationCompat.ProgressStyle.Point(MAX).setColor(ARRIVE),
                    ),
                )
            // A ticking countdown the system animates on its own, between location updates.
            content.countdownAt?.let {
                b.setShowWhen(true)
                    .setWhen(it)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
        }
        b.setStyle(style)
        return b.build()
    }

    /** Shown when a journey ends: a small receipt, with a one-tap save for new places. */
    fun summary(context: Context, plan: TripPlan, arrived: Boolean, travelledM: Double?, units: Units): Notification {
        val dest = plan.destination
        val minutes = ((System.currentTimeMillis() - plan.startedAt) / 60_000L).coerceAtLeast(1)
        val text = buildList {
            add(context.getString(R.string.minutes_short, minutes))
            travelledM?.takeIf { it > 50 }?.let { add(Format.distance(it, units).toString()) }
        }.joinToString(" · ")
        val b = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_nod)
            .setColor(ContextCompat.getColor(context, R.color.nod_lime_deep))
            .setContentTitle(context.getString(if (arrived) R.string.summary_arrived else R.string.summary_ended, dest.name))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60_000L)
            .setContentIntent(PendingIntent.getActivity(context, 12, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        if (plan.savedPlaceId == null) {
            b.addAction(
                R.drawable.ic_kind_place,
                context.getString(R.string.save),
                PendingIntent.getBroadcast(
                    context, 22,
                    Intent(context, NotificationActionReceiver::class.java)
                        .setAction(NotificationActionReceiver.ACTION_SAVE)
                        .putExtra(NotificationActionReceiver.EXTRA_PLAN, kotlinx.serialization.json.Json.encodeToString(TripPlan.serializer(), plan)),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        return b.build()
    }

    fun headsUp(context: Context, name: String): Notification =
        NotificationCompat.Builder(context, CH_HEADS_UP)
            .setSmallIcon(R.drawable.ic_stat_nod)
            .setColor(ContextCompat.getColor(context, R.color.nod_lime_deep))
            .setContentTitle(context.getString(R.string.notif_heads_up_title, name))
            .setContentText(context.getString(R.string.notif_heads_up_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(3 * 60_000L)
            .setContentIntent(openApp(context))
            .addAction(R.drawable.ic_moon, context.getString(R.string.action_up_end), service(context, TripService.ACTION_DISMISS, 23))
            .build()

    fun alarm(context: Context, name: String, distance: String?): Notification =
        NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_nod)
            .setColor(ContextCompat.getColor(context, R.color.nod_lime_deep))
            .setContentTitle(context.getString(R.string.notif_alarm_title, name))
            .setContentText(distance?.let { context.getString(R.string.notif_alarm_text, it) })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(alarmScreen(context), true)
            .setContentIntent(alarmScreen(context))
            .addAction(0, context.getString(R.string.action_awake), service(context, TripService.ACTION_DISMISS, 21))
            .build()

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
