package com.pascaldornfeld.gsdble.overview.components

import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.overview.fragments.SensorFragment
import com.pascaldornfeld.gsdble.overview.models.ImuConfig
import com.pascaldornfeld.gsdble.overview.models.ImuData

object SensorConfigManager {
    fun initialize(
        getView: ((Int) -> Fragment?),
        resetGraphFragments: (() -> Unit)
    ) {
        val vConfigSensor = getView(R.id.vConfigSensor) as SensorFragment
        vConfigSensor.setTitle("Sensor")
        vConfigSensor.functionToApply = {
            ImuConfig.actualOdr = if (it.ordinal == 0) ImuConfig.LSM_REAL_ODR else ImuConfig.BMI_REAL_ODR
            ImuData.actualTimeScaleMs = if (it.ordinal == 0) ImuData.TIME_SCALE_MS_LSM else ImuData.TIME_SCALE_MS_BMI
            resetGraphFragments()
            vConfigSensor.setNewData(it)
        }
        vConfigSensor.initWithLsm()
    }
}
