package com.pascaldornfeld.gsdble.overview.fragments

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.androidplot.Plot
import com.androidplot.PlotListener
import com.androidplot.SeriesRegistry
import com.androidplot.ui.Formatter
import com.androidplot.ui.SeriesBundle
import com.androidplot.ui.SeriesRenderer
import com.androidplot.util.PixelUtils
import com.androidplot.xy.XYGraphWidget
import com.androidplot.xy.XYPlot
import com.pascaldornfeld.gsdble.R
import kotlinx.android.synthetic.main.fragment_graph.*
import kotlinx.android.synthetic.main.fragment_graph.view.*
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import java.util.concurrent.atomic.AtomicBoolean

abstract class GraphFragment<DataType> : Fragment() {
    private val canUpdate = AtomicBoolean(true) // if data can be updated
    protected var data = listOf<Pair<Long, DataType>>() // snapshots of internalData for vPlot
    val internalData =
        mutableListOf<Pair<Long, DataType>>() // use only on the same thread as calling addData
    var showSeconds = 5 // show 5 seconds
    var timestepScalingMs: Float = 6.4f

    /** How to print values of DataType on the axis. Default implementation prints as Integer */
    open fun formatterY(obj: Number, toAppendTo: StringBuffer): StringBuffer =
        toAppendTo.append(obj.toLong())

    fun isPausedByUser(): Boolean = !(vCheckEnable?.isChecked ?: false)
    fun setTitle(title: String) {
        vTitle?.text = title
        vPlotGraph?.setTitle(title)
    }

    fun addData(p_time: Int, p_data: DataType) = addData(p_time.toLong(), p_data)

    /** add data series in this method */
    abstract fun seriesInit(plot: XYPlot)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)

        // init plotting graph
        seriesInit(view.vPlotGraph.apply {
            graph.apply {
                getLineLabelStyle(XYGraphWidget.Edge.LEFT).apply {
                    paint.textSize = PixelUtils.spToPix(9.0f)
                    format = object : Format() {
                        override fun format(
                            obj: Any,
                            toAppendTo: StringBuffer,
                            pos: FieldPosition
                        ): StringBuffer = formatterY(obj as Number, toAppendTo)

                        override fun parseObject(source: String, pos: ParsePosition): Any? = null
                    }
                }
                getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).apply {
                    paint.textSize = PixelUtils.spToPix(9.0f)
                    format = object : Format() {
                        override fun format(
                            obj: Any,
                            toAppendTo: StringBuffer,
                            pos: FieldPosition
                        ): StringBuffer = toAppendTo.append((obj as Number).toLong())

                        override fun parseObject(source: String, pos: ParsePosition): Any? = null
                    }
                }
            }
            addListener(object : PlotListener {
                override fun onAfterDraw(
                    source: Plot<*, out Formatter<*>, out SeriesRenderer<*, *, *>, out SeriesBundle<*, *>, out SeriesRegistry<*, *, *>>?,
                    canvas: Canvas?
                ) = canUpdate.set(true)

                override fun onBeforeDraw(
                    source: Plot<*, out Formatter<*>, out SeriesRenderer<*, *, *>, out SeriesBundle<*, *>, out SeriesRegistry<*, *, *>>?,
                    canvas: Canvas?
                ) = canUpdate.set(false)

            })
        })

        view.vCheckEnable.apply {
            setOnCheckedChangeListener { _, checked ->
                if (checked) vPlotGraph?.visibility = VISIBLE
                else vPlotGraph?.visibility = GONE
            }
            isChecked = false
            view.vPlotGraph.visibility = GONE
        }
        return view
    }

    fun addData(p_time: Long, p_data: DataType) {
        // add newest data
        internalData.add(Pair(p_time, p_data))

        // clear all in case of overflow of time variable on device
        if (internalData.first().first > p_time) internalData.clear()

        // remove too old data
        while (internalData.isNotEmpty() && internalData.first().first + (((showSeconds * 1000.0f) / timestepScalingMs)).toLong() < p_time)
            internalData.removeAt(0)

        // make snapshot and plot data if enabled and ready
        if (isResumed && !isPausedByUser() && canUpdate.compareAndSet(true, false)) {
            data = internalData.toList()
            vPlotGraph?.redraw()
        }

        afterAddingData(p_time, p_data)
    }

    /** New data can be printed into vCurValue */
    open fun afterAddingData(p_time: Long, p_data: DataType) = Unit
}