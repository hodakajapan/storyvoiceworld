package com.hodaka.storyvoice.ui.calendar

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hodaka.storyvoice.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class ReadingCalendarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val header: TextView
    private val btnPrev: TextView
    private val btnNext: TextView
    private val weekRow: LinearLayout
    private val recycler: RecyclerView

    private var ym: YearMonth = YearMonth.now()
    private var readDates: Set<LocalDate> = emptySet()

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_reading_calendar, this, true)
        header = findViewById(R.id.tvHeader)
        btnPrev = findViewById(R.id.btnPrevMonth)
        btnNext = findViewById(R.id.btnNextMonth)
        weekRow = findViewById(R.id.weekRow)
        recycler = findViewById(R.id.recyclerDays)
        recycler.layoutManager = GridLayoutManager(context, 7)
        recycler.adapter = DaysAdapter()

        setupWeekRow()
        attachHeaderActions()
        showMonth(YearMonth.now())
    }

    /** 外部から既読集合をセット（毎回再描画） */
    fun setReadDates(dates: Set<LocalDate>) {
        readDates = dates
        (recycler.adapter as DaysAdapter).submit(ym, readDates)
        recycler.requestLayout()
        recycler.invalidate()
    }

    /** 指定の月を表示する */
    fun showMonth(target: YearMonth) {
        ym = target
        header.text = "%d年 %d月".format(ym.year, ym.monthValue)
        (recycler.adapter as DaysAdapter).submit(ym, readDates)
    }

    /** 現在の月を返す（必要なら外部で使う） */
    fun currentMonth(): YearMonth = ym

    private fun attachHeaderActions() {
        btnPrev.setOnClickListener { showMonth(ym.minusMonths(1)) }
        btnNext.setOnClickListener { showMonth(ym.plusMonths(1)) }
    }

    private fun setupWeekRow() {
        weekRow.removeAllViews()
        val order = listOf(
            java.time.DayOfWeek.SUNDAY,
            java.time.DayOfWeek.MONDAY,
            java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
            java.time.DayOfWeek.SATURDAY
        )
        order.forEach { d ->
            val tv = TextView(context).apply {
                text = d.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                textSize = 12f
                setPadding(0, 4, 0, 4)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            weekRow.addView(tv)
        }
    }

    // ---------- Adapter ----------

    private inner class DaysAdapter : RecyclerView.Adapter<DayVH>() {
        private var cells: List<DayCell> = emptyList()

        fun submit(ym: YearMonth, readDates: Set<LocalDate>) {
            cells = buildCells(ym, readDates)
            notifyDataSetChanged()
            // ScrollView内で高さ再計測を促す
            (recycler.parent as? View)?.post { recycler.requestLayout() }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_day_cell, parent, false)
            return DayVH(v)
        }
        override fun getItemCount(): Int = cells.size
        override fun onBindViewHolder(holder: DayVH, position: Int) = holder.bind(cells[position])
    }

    private class DayVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv = v.findViewById<TextView>(R.id.tvDay)
        private val dot = v.findViewById<View>(R.id.readDot)

        fun bind(cell: DayCell) {
            if (cell.dayOfMonth == null) {
                tv.text = ""
                tv.setBackgroundColor(0x00000000)
                dot.visibility = View.GONE
            } else {
                // 日付表示（余分な「•」削除）
                tv.text = cell.dayOfMonth.toString()

                // 今日の日付を薄くハイライト
                tv.setBackgroundColor(if (cell.isToday) 0x22B3E5FC else 0x00000000)

                // 既読なら青ドット表示
                dot.visibility = if (cell.isRead) View.VISIBLE else View.GONE
            }
        }
    }
    }


    private data class DayCell(
        val dayOfMonth: Int?,
        val isRead: Boolean,
        val isToday: Boolean
    )

    private fun buildCells(ym: YearMonth, read: Set<LocalDate>): List<DayCell> {
        val firstOfMonth = ym.atDay(1)
        val daysInMonth = ym.lengthOfMonth()

        // ★ 日曜始まり: Sunday(7)→0, Monday(1)→1, ... Saturday(6)→6
        val offset = firstOfMonth.dayOfWeek.value % 7

        val today = LocalDate.now()
        val list = mutableListOf<DayCell>()
        repeat(offset) { list.add(DayCell(null, false, false)) }
        for (d in 1..daysInMonth) {
            val date = ym.atDay(d)
            list.add(DayCell(d, isRead = read.contains(date), isToday = date == today))
        }
        while (list.size % 7 != 0) list.add(DayCell(null, false, false))
        return list
    }

