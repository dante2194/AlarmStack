package app.alarmstack

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0F1020")
    private val card = Color.parseColor("#1A1B33")
    private val chipBg = Color.parseColor("#2B2D55")
    private val accent = Color.parseColor("#7C6BFF")
    private val teal = Color.parseColor("#1FD1C1")
    private val txt = Color.parseColor("#EDEDF7")
    private val sub = Color.parseColor("#9A9BB8")
    private val danger = Color.parseColor("#FF6B6B")
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private lateinit var root: FrameLayout
    private lateinit var profiles: MutableList<Profile>
    private var statusView: TextView? = null
    private var clockPkg: String? = "com.android.deskclock"
    private var running = false
    private var inEditor = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profiles = Store.load(this)
        root = FrameLayout(this)
        root.setBackgroundColor(bg)
        setContentView(root)
        showHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inEditor) showHome() else super.onBackPressed()
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun tv(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun btn(label: String, bgColor: Int, fg: Int = Color.WHITE, onClick: () -> Unit): TextView {
        val t = tv(label, 15f, fg, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(13), dp(16), dp(13))
        t.background = rounded(bgColor, 14)
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        val t = tv(label, 14f, txt, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(6), dp(10), dp(6), dp(10))
        t.background = rounded(chipBg, 10)
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun lpMatch(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(match, wrap)
        p.topMargin = dp(top)
        return p
    }

    private fun lpW(weight: Float): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, wrap, weight)
        p.setMargins(dp(3), dp(3), dp(3), dp(3))
        return p
    }

    private fun vbox(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    private fun hbox(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        return l
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun fmt(h: Int, m: Int): String = String.format(Locale.US, "%02d:%02d", h, m)

    private fun daysText(m: Int): String {
        if (m == 0) return "once"
        if (m == 127) return "every day"
        if (m == 31) return "Mon–Fri"
        if (m == 96) return "Sat–Sun"
        val names = ArrayList<String>()
        for (i in 0..6) if (((m shr i) and 1) == 1) names.add(dayNames[i])
        return names.joinToString(" ")
    }

    private fun headline(s: Series): String =
        fmt(s.hour, s.minute) + "  ×" + s.count + (if (s.count > 1) "  ·  every ${s.interval} min" else "")

    private fun detail(s: Series, fallback: String): String {
        val parts = ArrayList<String>()
        if (s.count > 1) {
            val total = s.hour * 60 + s.minute + (s.count - 1) * s.interval
            val d = total / 1440
            parts.add("until " + fmt((total % 1440) / 60, total % 60) + (if (d > 0) " (+${d}d)" else ""))
        }
        parts.add(daysText(s.days))
        parts.add(if (s.label.isBlank()) "“$fallback”" else "“${s.label}”")
        return parts.joinToString(" · ")
    }

    // ------------------------------------------------------------------- home

    private fun showHome() {
        inEditor = false
        root.removeAllViews()
        val col = vbox()
        col.setPadding(dp(20), dp(24), dp(20), dp(16))
        col.addView(tv("Alarm Stack", 30f, txt, true))
        col.addView(tv("Tap Run to set every alarm in a profile", 14f, sub, false), lpMatch(2))

        val st = tv("", 14f, teal, true)
        statusView = st
        col.addView(st, lpMatch(10))

        val list = vbox()
        for (p in profiles) list.addView(profileCard(p), lpMatch(12))
        val sv = ScrollView(this)
        sv.addView(list)
        col.addView(sv, LinearLayout.LayoutParams(match, 0, 1f))

        col.addView(btn("＋  New profile", accent) { showEditor(Profile(), true) }, lpMatch(12))
        root.addView(col, FrameLayout.LayoutParams(match, match))
    }

    private fun profileCard(p: Profile): View {
        val c = vbox()
        c.setPadding(dp(16), dp(16), dp(16), dp(12))
        c.background = rounded(card, 20)

        val n = p.totalAlarms()
        c.addView(tv(p.name, 20f, txt, true))
        c.addView(
            tv("$n alarm${if (n == 1) "" else "s"} · ${p.series.size} series", 13f, teal, true),
            lpMatch(2)
        )
        var shown = 0
        for (s in p.series) {
            if (shown == 4) {
                c.addView(tv("…", 13f, sub, false), lpMatch(2))
                break
            }
            c.addView(tv(headline(s), 13f, sub, false), lpMatch(2))
            shown++
        }

        val row = hbox()
        row.addView(btn("▶  Run · $n", accent) { runProfile(p) }, lpW(2f))
        row.addView(btn("Edit", chipBg) { showEditor(p, false) }, lpW(1f))
        row.addView(btn("✕", chipBg, danger) { confirmDelete(p) }, lpW(0.6f))
        c.addView(row, lpMatch(12))
        return c
    }

    private fun confirmDelete(p: Profile) {
        AlertDialog.Builder(this)
            .setTitle("Delete “${p.name}”?")
            .setMessage("Only the profile is removed. Alarms already in the Clock app stay.")
            .setPositiveButton("Delete") { _, _ ->
                profiles.removeAll { it.id == p.id }
                Store.save(this, profiles)
                showHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- running

    private fun fireWithFallback(slot: Slot): Boolean {
        if (Planner.fire(this, slot, clockPkg)) return true
        if (clockPkg != null) {
            clockPkg = null // target app missing: let Android pick any clock app
            return Planner.fire(this, slot, null)
        }
        return false
    }

    private fun runProfile(p: Profile) {
        if (running) {
            toast("Still working…")
            return
        }
        val slots = ArrayList<Slot>()
        for (s in p.series) {
            for (slot in Planner.expand(s)) {
                slots.add(if (slot.label.isBlank()) slot.copy(label = p.name) else slot)
            }
        }
        if (slots.isEmpty()) {
            toast("Add a series first")
            return
        }
        running = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val total = slots.size
        var idx = 0
        var ok = 0
        val step = object : Runnable {
            override fun run() {
                if (idx < total) {
                    if (fireWithFallback(slots[idx])) ok++
                    idx++
                    statusView?.text = "Setting alarms… $idx / $total"
                    handler.postDelayed(this, 200L)
                } else {
                    running = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    statusView?.text =
                        if (ok == total) "✓ Set $ok alarms for ${p.name}"
                        else "Set $ok of $total — check Clock app / permissions"
                }
            }
        }
        handler.post(step)
    }

    // ----------------------------------------------------------------- editor

    private fun showEditor(orig: Profile, isNew: Boolean) {
        inEditor = true
        root.removeAllViews()
        val work = Profile(orig.id, orig.name, orig.series.map { it.copy() }.toMutableList())

        val col = vbox()
        col.setPadding(dp(20), dp(24), dp(20), dp(16))
        col.addView(tv(if (isNew) "New profile" else "Edit profile", 26f, txt, true))

        val nameEt = EditText(this)
        nameEt.setText(work.name)
        nameEt.hint = "Profile name"
        nameEt.setTextColor(txt)
        nameEt.setHintTextColor(sub)
        nameEt.textSize = 18f
        nameEt.setSingleLine(true)
        nameEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        col.addView(nameEt, lpMatch(12))

        val totalView = tv("", 13f, teal, true)
        col.addView(totalView, lpMatch(8))

        val list = vbox()
        var rebuild: () -> Unit = {}
        rebuild = {
            list.removeAllViews()
            for (s in work.series) list.addView(seriesRow(work, s) { rebuild() }, lpMatch(8))
            totalView.text = "${work.totalAlarms()} alarms in this profile"
        }
        rebuild()

        val body = vbox()
        body.addView(list)
        body.addView(btn("＋  Add alarm series", chipBg) {
            val s = Series()
            editSeries(s) {
                work.series.add(s)
                rebuild()
            }
        }, lpMatch(12))
        val sv = ScrollView(this)
        sv.addView(body)
        col.addView(sv, LinearLayout.LayoutParams(match, 0, 1f))

        val bottom = hbox()
        bottom.addView(btn("Cancel", chipBg) { showHome() }, lpW(1f))
        bottom.addView(btn("Save", accent) {
            work.name = nameEt.text.toString().trim().ifEmpty { "Profile" }
            val idx = profiles.indexOfFirst { it.id == work.id }
            if (idx >= 0) profiles[idx] = work else profiles.add(work)
            Store.save(this, profiles)
            showHome()
        }, lpW(2f))
        col.addView(bottom, lpMatch(12))

        root.addView(col, FrameLayout.LayoutParams(match, match))
    }

    private fun seriesRow(profile: Profile, s: Series, onChange: () -> Unit): View {
        val row = hbox()
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(12), dp(6), dp(12))
        row.background = rounded(card, 16)
        row.isClickable = true
        row.setOnClickListener { editSeries(s) { onChange() } }

        val info = vbox()
        info.addView(tv(headline(s), 17f, txt, true))
        info.addView(tv(detail(s, profile.name), 13f, sub, false))
        row.addView(info, LinearLayout.LayoutParams(0, wrap, 1f))

        val x = tv("✕", 18f, danger, true)
        x.setPadding(dp(14), dp(8), dp(14), dp(8))
        x.setOnClickListener {
            profile.series.removeAll { it === s }
            onChange()
        }
        row.addView(x)
        return row
    }

    private fun editSeries(s: Series, onSaved: () -> Unit) {
        val w = s.copy()
        val box = vbox()
        box.setPadding(dp(20), dp(8), dp(20), dp(0))
        var refresh: () -> Unit = {}

        val timeB = btn("", chipBg) {
            TimePickerDialog(this, { _, h, m ->
                w.hour = h
                w.minute = m
                refresh()
            }, w.hour, w.minute, true).show()
        }
        val countB = btn("", chipBg) {
            pickNumber("How many alarms (1–$MAX_COUNT)", 1, MAX_COUNT, w.count, intArrayOf(1, 5, 10, 20, 50, 100)) {
                w.count = it
                refresh()
            }
        }
        val intervalB = btn("", chipBg) {
            pickNumber("Minutes between alarms", 1, 180, w.interval, intArrayOf(1, 2, 3, 5, 10, 15, 30)) {
                w.interval = it
                refresh()
            }
        }
        val daysB = btn("", chipBg) {
            pickDays(w.days) {
                w.days = it
                refresh()
            }
        }
        val labelEt = EditText(this)
        labelEt.setText(w.label)
        labelEt.hint = "Label (blank = profile name)"
        labelEt.setTextColor(txt)
        labelEt.setHintTextColor(sub)
        labelEt.setSingleLine(true)

        box.addView(tv("Start time", 12f, sub, false), lpMatch(8))
        box.addView(timeB, lpMatch(2))
        box.addView(tv("Number of alarms", 12f, sub, false), lpMatch(10))
        box.addView(countB, lpMatch(2))
        box.addView(tv("Spacing", 12f, sub, false), lpMatch(10))
        box.addView(intervalB, lpMatch(2))
        box.addView(tv("Repeat", 12f, sub, false), lpMatch(10))
        box.addView(daysB, lpMatch(2))
        box.addView(tv("Label", 12f, sub, false), lpMatch(10))
        box.addView(labelEt, lpMatch(2))

        refresh = {
            timeB.text = fmt(w.hour, w.minute)
            countB.text = "${w.count} alarm${if (w.count == 1) "" else "s"}"
            intervalB.text = if (w.count > 1) "every ${w.interval} min" else "single alarm"
            daysB.text = daysText(w.days)
        }
        refresh()

        val sv = ScrollView(this)
        sv.addView(box)
        AlertDialog.Builder(this)
            .setTitle("Alarm series")
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                s.hour = w.hour
                s.minute = w.minute
                s.count = w.count
                s.interval = w.interval
                s.days = w.days
                s.label = labelEt.text.toString().trim()
                onSaved()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickNumber(title: String, min: Int, max: Int, cur: Int, quick: IntArray, done: (Int) -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(3))
        et.textSize = 28f
        et.gravity = Gravity.CENTER
        et.setTextColor(txt)

        fun setNum(v: Int) {
            et.setText(v.coerceIn(min, max).toString())
            et.setSelection(et.text.length)
        }
        fun bump(d: Int) {
            setNum((et.text.toString().toIntOrNull() ?: cur) + d)
        }
        setNum(cur)

        val box = vbox()
        box.setPadding(dp(20), dp(8), dp(20), dp(0))
        box.addView(et, lpMatch(0))

        val r1 = hbox()
        r1.addView(chip("−  1") { bump(-1) }, lpW(1f))
        r1.addView(chip("+  1") { bump(1) }, lpW(1f))
        box.addView(r1, lpMatch(8))

        val r2 = hbox()
        for (q in quick) r2.addView(chip("$q") { setNum(q) }, lpW(1f))
        box.addView(r2, lpMatch(4))

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("OK") { _, _ ->
                done((et.text.toString().toIntOrNull() ?: cur).coerceIn(min, max))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDays(mask: Int, done: (Int) -> Unit) {
        val checked = BooleanArray(7) { ((mask shr it) and 1) == 1 }
        AlertDialog.Builder(this)
            .setTitle("Repeat on (none = once)")
            .setMultiChoiceItems(dayNames, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("OK") { _, _ ->
                var m = 0
                for (i in 0..6) if (checked[i]) m = m or (1 shl i)
                done(m)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
