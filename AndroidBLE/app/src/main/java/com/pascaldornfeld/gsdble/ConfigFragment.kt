package com.pascaldornfeld.gsdble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment


abstract class ConfigFragment<DataType> : Fragment() {
    var vTitle: TextView? = null
    var vSlider: SeekBar? = null
    var vCurrent: TextView? = null
    var vSet: TextView? = null
    var vApply: Button? = null

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
    abstract fun applyNewData(index: Int)

    fun setNewData(data: DataType) {
        val stringRep = getStringRepresentationFromData(data)
        val index = getDataArray().indexOf(data)
        if (index != -1) {
            vSlider!!.progress = index
            vCurrent!!.text = stringRep
        }
        vSet!!.text = stringRep
    }

    fun setTitle(title: String) {
        vTitle!!.text = title
    }
}

class OdrFragment : ConfigFragment<ImuOdr>() {
    private val odrs by lazy { ImuOdr.values() }
    var functionToApply: ((ImuOdr) -> Unit)? = null

    override fun getDataArray(): Array<ImuOdr> = odrs

    override fun getStringRepresentationFromData(data: ImuOdr): String = data.name

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(odrs[index])
    }

}