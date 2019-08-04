package com.pascaldornfeld.gsdble.overview.fragments

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.CheckBox
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
import com.pascaldornfeld.gsdble.overview.models.ImuData
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import java.util.concurrent.atomic.AtomicBoolean

abstract class GraphFragment<DataType> : Fragment() {
    protected var data = listOf<Pair<Long, DataType>>() // snapshots of internalData for vPlot
    val internalData = mutableListOf<Pair<Long, DataType>>() // use only on the same thread as calling addData
    private var vPlot: XYPlot? = null
    private var vCheckBox: CheckBox? = null
    open var timeToShowMs = 5000.0f // show 5 seconds
        protected set(value) {
            field = value
        }
    var timestepScalingMs: (() -> Float) = { ImuData.actualTimeScaleMs }

    private fun timeInXScalingToShow() = ((timeToShowMs / timestepScalingMs())).toInt()

    open fun formatterY(obj: Number, toAppendTo: StringBuffer): StringBuffer = toAppendTo.append(obj.toLong())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        vPlot = view.findViewById(R.id.vPlotGraph)

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

        vCheckBox = view.findViewById(R.id.vCheckEnable)
        vCheckBox!!.setOnCheckedChangeListener { _, checked ->
            if (checked) vPlot!!.visibility = VISIBLE
            else vPlot!!.visibility = GONE
        }
        vCheckBox!!.isChecked = false
        return view
    }

    fun isPaused(): Boolean {
        val check = vCheckBox
        return check == null || !check.isChecked
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

        if (vCheckBox?.isChecked == true && canUpdate.compareAndSet(true, false)) {
            data = internalData.toList()
            vPlot?.redraw()
        }

    }

    fun addData(p_time: Int, p_data: DataType) {
        addData(p_time.toLong(), p_data)
    }

    abstract fun seriesInit(plot: XYPlot)
}