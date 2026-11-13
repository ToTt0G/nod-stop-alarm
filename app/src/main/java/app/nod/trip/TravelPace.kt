package app.nod.trip

/**
 * Estimates the speed to expect for the rest of the ride, from how fast you've been travelling.
 *
 * Only time spent moving is recorded, in boxes of one minute of moving time, so stops at stations,
 * bus stops and lights neither slow the estimate down nor make it forget. The estimate is the
 * average over a window of recent boxes, chosen like this:
 *
 * - **Current pace.** The last few minutes set the reference pace. Walking back from the newest box,
 *   the window stops at the first clear change of pace (more than [CHANGE_FACTOR] faster or slower),
 *   e.g. where a fast intercity stretch gave way to a slow crawl into a city. A single odd box is
 *   tolerated, so one slow minute (braking, a signal) isn't mistaken for a new pace.
 * - **Horizon.** The window never looks further back than the time still to travel: with 40 min to
 *   go, the pace of the last 40 min is the best guess; with 3 min to go, only the last few minutes
 *   matter.
 * - At least [MIN_WINDOW_SEC] of movement is always used, to smooth out GPS noise.
 *
 * Pure Kotlin, no Android types, so it can be unit tested.
 */
class TravelPace {
    private class Box(var m: Double = 0.0, var sec: Double = 0.0) {
        val mps get() = m / sec
    }

    // Newest last. The last box is the one being filled.
    private val boxes = ArrayDeque<Box>()
    private var totalM = 0.0
    private var totalSec = 0.0

    /** Records [m] metres covered in [sec] seconds of travel, spread over the boxes it spans. */
    fun add(m: Double, sec: Double) {
        if (sec <= 0.0) return
        totalM += m
        totalSec += sec
        var left = sec
        while (left > 1e-9) {
            val box = boxes.lastOrNull()?.takeIf { it.sec < BOX_SEC - 1e-9 } ?: Box().also { boxes.addLast(it) }
            val take = minOf(left, BOX_SEC - box.sec)
            box.m += m * take / sec
            box.sec += take
            left -= take
        }
        while (boxes.size > MAX_BOXES) boxes.removeFirst()
    }

    /**
     * Expected speed for the rest of the ride, given [remainingM] still to go; null until enough
     * movement has been seen to trust it.
     */
    fun speedFor(remainingM: Double): Double? {
        if (totalSec < MIN_MOVING_SEC || totalM < MIN_MOVING_M) return null
        val reference = averageOfNewest(MIN_WINDOW_SEC)
        val horizonSec = (remainingM / reference).coerceIn(MIN_WINDOW_SEC, MAX_BOXES * BOX_SEC)

        var m = 0.0
        var sec = 0.0
        var i = boxes.lastIndex
        while (i >= 0 && sec < horizonSec) {
            val box = boxes[i]
            // The newest few minutes are the reference itself; past them, stop at a change of pace.
            if (sec >= MIN_WINDOW_SEC && !samePace(box.mps, reference)) {
                // One odd box is fine if the one before it is back at the current pace.
                val older = boxes.getOrNull(i - 1)
                if (older == null || !samePace(older.mps, reference)) break
            }
            val take = minOf(box.sec, horizonSec - sec)
            m += box.m * take / box.sec
            sec += take
            i--
        }
        return m / sec
    }

    private fun averageOfNewest(windowSec: Double): Double {
        var m = 0.0
        var sec = 0.0
        for (i in boxes.indices.reversed()) {
            val box = boxes[i]
            val take = minOf(box.sec, windowSec - sec)
            m += box.m * take / box.sec
            sec += take
            if (sec >= windowSec) break
        }
        return m / sec
    }

    private fun samePace(a: Double, b: Double) = a <= b * CHANGE_FACTOR && b <= a * CHANGE_FACTOR

    companion object {
        const val BOX_SEC = 60.0
        /** An hour of moving time is plenty of history. */
        const val MAX_BOXES = 60
        const val MIN_WINDOW_SEC = 180.0
        /** More than this much faster or slower than the current pace counts as a change of pace. */
        const val CHANGE_FACTOR = 1.6
        /** Movement needed before the estimate replaces the current speed. */
        const val MIN_MOVING_SEC = 15.0
        const val MIN_MOVING_M = 100.0
    }
}
