package com.pascaldornfeld.gsdble.fragments

import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.androidplot.Plot
import com.androidplot.PlotListener
import com.androidplot.SeriesRegistry
import com.androidplot.ui.Formatter
import com.androidplot.ui.SeriesBundle
import com.androidplot.ui.SeriesRenderer
import com.androidplot.util.PixelUtils
import com.androidplot.xy.*
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.models.ImuData
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import java.util.concurrent.atomic.AtomicBoolean

abstract class GraphFragment<DataType> : Fragment() {
    protected var data = listOf<Pair<Long, DataType>>()
    val internalData = mutableListOf<Pair<Long, DataType>>()
    // use only on the same thread as calling addData
    private var vPlot: XYPlot? = null
    open var timeToShowMs = 5000.0f // show 5 seconds
        protected set(value) {
            field = value
        }
    var timestepScalingMs: (() -> Float) = { ImuData.actualTimeScaleMs }

    private fun timeInXScalingToShow() = ((timeToShowMs / timestepScalingMs())).toInt()

    open fun formatterY(obj: Number, toAppendTo: StringBuffer): StringBuffer = toAppendTo.append(obj.toLong())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        vPlot = view.findViewById(R.id.plot_graph)

        vPlot!!.graph?.getLineLabelStyle(XYGraphWidget.Edge.LEFT)?.paint?.textSize = PixelUtils.spToPix(9.0f)
        vPlot!!.graph?.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM)?.paint?.textSize = PixelUtils.spToPix(9.0f)

        val formatX = object : Format() {
            override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
                toAppendTo.append((obj as Number).toLong())

            override fun parseObject(source: String, pos: ParsePosition): Any? {
                return null
            }
        }
        val formatY = object : Format() {
            override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
                formatterY(obj as Number, toAppendTo)

            override fun parseObject(source: String, pos: ParsePosition): Any? {
                return null
            }
        }
        vPlot!!.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = formatX
        vPlot!!.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = formatY

        vPlot!!.addListener(object : PlotListener {
            override fun onAfterDraw(
                source: Plot<*, out Formatter<*>, out SeriesRenderer<*, *, *>, out SeriesBundle<*, *>, out SeriesRegistry<*, *, *>>?,
                canvas: Canvas?
            ) {
                canUpdate.set(true)
            }

            override fun onBeforeDraw(
                source: Plot<*, out Formatter<*>, out SeriesRenderer<*, *, *>, out SeriesBundle<*, *>, out SeriesRegistry<*, *, *>>?,
                canvas: Canvas?
            ) {
                canUpdate.set(false)
            }

        })

        seriesInit(vPlot!!)
        return view
    }

    fun setTitle(title: String) {
        vPlot?.setTitle(title)
    }

    private val canUpdate = AtomicBoolean(true)

    fun addData(p_time: Long, p_data: DataType) {
        internalData.add(Pair(p_time, p_data))
        if (internalData.first().first > p_time) internalData.clear() // in case of overflow

        while (internalData.isNotEmpty() && internalData.first().first + timeInXScalingToShow() < p_time) {
            internalData.removeAt(0)
        }

        if (canUpdate.compareAndSet(true, false)) {
            data = internalData.toList()
            vPlot?.redraw()
        }

    }

    fun addData(p_time: Int, p_data: DataType) {
        addData(p_time.toLong(), p_data)
    }

    abstract fun seriesInit(plot: XYPlot)
}

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
class FloatTimeGraphFragment : GraphFragment<Float>() {
    var plotplotTitle = ""

    fun initialize(p_timeToShowMs: Float?, p_timeStepScalingMs: Float?, p_title: String) {
        if (p_timeStepScalingMs != null) timestepScalingMs = {p_timeStepScalingMs}
        if (p_timeToShowMs != null) timeToShowMs = p_timeToShowMs
        plotplotTitle = p_title
    }

    override fun formatterY(obj: Number, toAppendTo: StringBuffer): StringBuffer =
        toAppendTo.append(String.format("%.2f", obj.toFloat()))

    override fun seriesInit(plot: XYPlot) {
        val series = object : FastXYSeries {
            override fun getX(index: Int): Number = data[index].first

            override fun getY(index: Int): Number = data[index].second

            override fun minMax(): RectRegion {
                val minX = if (data.isEmpty()) 0 else data.minBy { it.first }!!.first
                val maxX = if (data.isEmpty()) 0 else data.maxBy { it.first }!!.first
                val minY = if (data.isEmpty()) 0.0f else data.minBy { it.second }!!.second
                val maxY = if (data.isEmpty()) 0.0f else data.maxBy { it.second }!!.second
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

    fun initialize(p_timeToShowMs: Float?, p_timeStepScalingMs: Float?, p_title: String) {
        if (p_timeStepScalingMs != null) timestepScalingMs = {p_timeStepScalingMs}
        if (p_timeToShowMs != null) timeToShowMs = p_timeToShowMs
        plotplotTitle = p_title
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