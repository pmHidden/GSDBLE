package com.pascaldornfeld.gsdble.overview.fragments

import android.graphics.Color
import android.util.Log
import androidx.core.content.ContextCompat
import com.androidplot.xy.FastLineAndPointRenderer
import com.androidplot.xy.FastXYSeries
import com.androidplot.xy.RectRegion
import com.androidplot.xy.XYPlot
import com.pascaldornfeld.gsdble.R

/**
 * graph to visualize time vs 3 sensor axis data (f.e. gyro or accel data)
 */
class SensorGraphFragment : GraphFragment<Triple<Short, Short, Short>>() {

    override fun seriesInit(plot: XYPlot) {
        for (i in 0..2) {
            val axisSeries = object : FastXYSeries {
                override fun getX(index: Int): Number = data[index].first

                override fun getY(index: Int): Number = data[index].second.toList()[i]

                override fun minMax(): RectRegion {
                    val minX = if (data.isEmpty()) 0 else data[0].first
                    val maxX = if (data.isEmpty()) 0 else data[data.lastIndex].first
                    return RectRegion(minX, maxX, Short.MIN_VALUE, Short.MAX_VALUE)
                }

                override fun getTitle(): String {
                    return when {
                        i % 3 == 0 -> "X-Axis"
                        i % 3 == 1 -> "Y-Axis"
                        else -> "Z-Axis"
                    }
                }

                override fun size(): Int = data.size
            }
            val r = if (i % 3 == 0) 255 else 0
            val g = if (i % 3 == 1) 255 else 0
            val b = if (i % 3 == 2) 255 else 0
            plot.addSeries(axisSeries, FastLineAndPointRenderer.Formatter(Color.rgb(r, g, b), null, null))
        }
    }
}

/**
 * graph to visualize realtime time vs aby number
 */
class DoubleTimeGraphFragment : GraphFragment<Double>() {
    var plotplotTitle = ""

    fun initialize(p_timeToShowMs: Float?, p_timeStepScalingMs: Float?, p_title: String): DoubleTimeGraphFragment {
        if (p_timeStepScalingMs != null) timestepScalingMs = { p_timeStepScalingMs }
        if (p_timeToShowMs != null) timeToShowMs = p_timeToShowMs
        plotplotTitle = p_title
        return this
    }

    override fun formatterY(obj: Number, toAppendTo: StringBuffer): StringBuffer =
        toAppendTo.append(String.format("%.2f", obj.toDouble()))

    override fun addData(p_time: Long, p_data: Double) {
        super.addData(p_time, p_data)
        vCurValue?.text = String.format("%.2f", p_data)
    }

    override fun seriesInit(plot: XYPlot) {
        val series = object : FastXYSeries {
            override fun getX(index: Int): Number = data[index].first

            override fun getY(index: Int): Number = data[index].second

            override fun minMax(): RectRegion {
                val minX = if (data.isEmpty()) 0 else data.minBy { it.first }!!.first
                val maxX = if (data.isEmpty()) 0 else data.maxBy { it.first }!!.first
                val minY = if (data.isEmpty()) 0.0 else data.minBy { it.second }!!.second
                val maxY = if (data.isEmpty()) 0.0 else data.maxBy { it.second }!!.second
                return RectRegion(minX, maxX, minY, maxY)
            }

            override fun getTitle(): String = plotplotTitle

            override fun size(): Int = data.size
        }

        val context = this.context
        if (context != null)
            plot.addSeries(
                series,
                FastLineAndPointRenderer.Formatter(ContextCompat.getColor(context, R.color.colorAccent), null, null)
            )
        else Log.w("GraphFragment", "Cound not get context")
    }
}

/**
 * graph to visualize realtime time vs aby number
 */
class LongTimeGraphFragment : GraphFragment<Long>() {
    var plotplotTitle = ""

    fun initialize(p_timeToShowMs: Float?, p_timeStepScalingMs: Float?, p_title: String): LongTimeGraphFragment {
        if (p_timeStepScalingMs != null) timestepScalingMs = { p_timeStepScalingMs }
        if (p_timeToShowMs != null) timeToShowMs = p_timeToShowMs
        plotplotTitle = p_title
        return this
    }

    override fun addData(p_time: Long, p_data: Long) {
        super.addData(p_time, p_data)
        vCurValue?.text = p_data.toString()
    }

    override fun seriesInit(plot: XYPlot) {
        val series = object : FastXYSeries {
            override fun getX(index: Int): Number = data[index].first

            override fun getY(index: Int): Number = data[index].second

            override fun minMax(): RectRegion {
                val minX = if (data.isEmpty()) 0 else data.minBy { it.first }!!.first
                val maxX = if (data.isEmpty()) 0 else data.maxBy { it.first }!!.first
                val minY = if (data.isEmpty()) 0 else data.minBy { it.second }!!.second
                val maxY = if (data.isEmpty()) 0 else data.maxBy { it.second }!!.second
                return RectRegion(minX, maxX, minY, maxY)
            }

            override fun getTitle(): String = plotplotTitle

            override fun size(): Int = data.size
        }

        val context = this.context
        if (context != null)
            plot.addSeries(
                series,
                FastLineAndPointRenderer.Formatter(ContextCompat.getColor(context, R.color.colorAccent), null, null)
            )
        else Log.w("GraphFragment", "Cound not get context")
    }
}