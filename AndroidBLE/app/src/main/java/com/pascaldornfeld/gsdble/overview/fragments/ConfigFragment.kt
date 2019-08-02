package com.pascaldornfeld.gsdble.overview.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.models.ImuOdr


abstract class ConfigFragment<DataType> : Fragment() {
    var vTitle: TextView? = null
    var vSlider: SeekBar? = null
    var vCurrent: TextView? = null
    var vSet: TextView? = null
    var vApply: Button? = null
    abstract var functionToApply: ((DataType) -> Unit)?
    private val prefixSet = "on device: "

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_config, container, false)
        vTitle = view.findViewById(R.id.tv_title)
        vSlider = view.findViewById(R.id.sk_slider)
        vCurrent = view.findViewById(R.id.tv_current)
        vSet = view.findViewById(R.id.tv_set)
        vApply = view.findViewById(R.id.bt_apply)

        vSlider!!.progress = 0
        vSlider!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                vCurrent!!.text = getStringRepresentationFromData(getDataArray()[progress])
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        vSlider!!.max = getDataArray().size - 1
        vApply!!.setOnClickListener { applyNewData(vSlider!!.progress) }

        return view
    }

    abstract fun getDataArray(): Array<DataType>
    abstract fun getStringRepresentationFromData(data: DataType): String
    protected abstract fun applyNewData(index: Int)

    @SuppressLint("SetTextI18n")
    fun setNewData(data: DataType) {
        val stringRep = getStringRepresentationFromData(data)
        val index = getDataArray().indexOf(data)
        if (index != -1) {
            vSlider!!.progress = index
            vCurrent!!.text = stringRep
        }
        vSet!!.text = prefixSet + stringRep
    }

    fun setTitle(title: String) {
        vTitle!!.text = title
    }
}

class OdrFragment : ConfigFragment<ImuOdr>() {
    private val odrs by lazy { ImuOdr.values() }
    override var functionToApply: ((ImuOdr) -> Unit)? = null

    override fun getDataArray(): Array<ImuOdr> = odrs

    override fun getStringRepresentationFromData(data: ImuOdr): String = data.name

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(odrs[index])
    }
}

class PauseFragment : ConfigFragment<Boolean>() {
    override var functionToApply: ((Boolean) -> Unit)? = null

    override fun getDataArray(): Array<Boolean> = arrayOf(false, true)

    override fun getStringRepresentationFromData(data: Boolean): String = data.toString()

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(index == 1)
    }
}

class IntervalFragment : ConfigFragment<IntervalFragment.Interval>() {
    enum class Interval { CONNECTION_PRIORITY_LOW_POWER, CONNECTION_PRIORITY_BALANCED, CONNECTION_PRIORITY_HIGH }

    private val intervals by lazy { Interval.values() }
    override var functionToApply: ((Interval) -> Unit)? = null

    override fun getDataArray(): Array<Interval> = intervals

    override fun getStringRepresentationFromData(data: Interval): String = data.name

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(intervals[index])
    }
}

class MtuFragment : ConfigFragment<Int>() {
    override var functionToApply: ((Int) -> Unit)? = null
    private val minimum = 23
    private val maximum = 517

    override fun getDataArray(): Array<Int> = IntArray(maximum - minimum + 1) { minimum + it }.toTypedArray()
    override fun getStringRepresentationFromData(data: Int): String = data.toString()

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(index + minimum)
    }
}

class SensorFragment : ConfigFragment<SensorFragment.Sensors>() {
    enum class Sensors { LSM6DSL, BMI160 }
    private val sensors by lazy { Sensors.values() }

    fun initWithLsm() { applyNewData(0) }

    override var functionToApply: ((Sensors) -> Unit)? = null
    override fun getDataArray(): Array<Sensors> = sensors

    override fun getStringRepresentationFromData(data: Sensors): String = data.name

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(sensors[index])
    }
}