package app.alarmstack

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val MAX_COUNT = 100

/** One series: "count" alarms starting at hour:minute, "interval" minutes apart. */
data class Series(
    var hour: Int = 6,
    var minute: Int = 0,
    var count: Int = 1,
    var interval: Int = 5,
    var days: Int = 0, // bit0 = Mon ... bit6 = Sun, 0 = one-time
    var label: String = ""
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New profile",
    val series: MutableList<Series> = mutableListOf()
) {
    fun totalAlarms(): Int {
        var n = 0
        for (s in series) n += s.count
        return n
    }
}

data class Slot(val hour: Int, val minute: Int, val days: Int, val label: String)

object Planner {

    fun expand(s: Series): List<Slot> {
        val out = ArrayList<Slot>()
        val start = s.hour * 60 + s.minute
        for (i in 0 until s.count) {
            val t = start + i * s.interval
            val m = t % 1440
            out.add(Slot(m / 60, m % 60, rotate(s.days, t / 1440), s.label))
        }
        return out
    }

    // Alarms that spill past midnight land on the next weekday.
    private fun rotate(mask: Int, by: Int): Int {
        if (mask == 0) return 0
        var m = mask
        repeat(by % 7) { m = ((m shl 1) or (m shr 6)) and 0x7F }
        return m
    }

    fun fire(ctx: Context, s: Slot, pkg: String?): Boolean {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, s.hour)
        i.putExtra(AlarmClock.EXTRA_MINUTES, s.minute)
        i.putExtra(AlarmClock.EXTRA_MESSAGE, s.label)
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (s.days != 0) {
            val list = ArrayList<Int>()
            for (b in 0..6) {
                if (((s.days shr b) and 1) == 1) list.add((b + 1) % 7 + 1) // Calendar.MONDAY=2 ... SUNDAY=1
            }
            i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, list)
        }
        if (pkg != null) i.setPackage(pkg)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}

object Store {
    private const val PREFS = "alarmstack"
    private const val KEY = "profiles"

    fun load(ctx: Context): MutableList<Profile> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw == null) {
            val seeded = seed()
            save(ctx, seeded)
            return seeded
        }
        val out = ArrayList<Profile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val p = Profile(o.getString("id"), o.getString("name"))
                val sa = o.getJSONArray("series")
                for (j in 0 until sa.length()) {
                    val s = sa.getJSONObject(j)
                    p.series.add(
                        Series(
                            s.getInt("hour"),
                            s.getInt("minute"),
                            s.getInt("count").coerceIn(1, MAX_COUNT),
                            s.getInt("interval").coerceAtLeast(1),
                            s.getInt("days"),
                            s.optString("label", "")
                        )
                    )
                }
                out.add(p)
            }
        } catch (e: Exception) {
            // corrupted data: fall through with whatever loaded
        }
        return out
    }

    fun save(ctx: Context, list: List<Profile>) {
        val arr = JSONArray()
        for (p in list) {
            val sa = JSONArray()
            for (s in p.series) {
                sa.put(
                    JSONObject()
                        .put("hour", s.hour)
                        .put("minute", s.minute)
                        .put("count", s.count)
                        .put("interval", s.interval)
                        .put("days", s.days)
                        .put("label", s.label)
                )
            }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("series", sa))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun seed(): MutableList<Profile> {
        val school = Profile(
            name = "School Day",
            series = mutableListOf(
                Series(6, 0, 20, 5, 31, "School"),
                Series(12, 0, 1, 5, 31, "Lunch")
            )
        )
        return mutableListOf(school)
    }
}
