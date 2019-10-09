package com.pascaldornfeld.gsdble.connected.async_calculations

import com.pascaldornfeld.gsdble.connected.SmallDataHolder
import kotlin.math.pow
import kotlin.math.sqrt

class CharaDataStatsBySecondValue(
    timestamp: Long,
    averageDataHolder: SmallDataHolder<Double>?,
    private val deviationDataHolder: SmallDataHolder<Double>?
) : CharaDataStatsCalculatorAsyncTask(timestamp, averageDataHolder, deviationDataHolder) {

    override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Double? {
        assert(params.isNotEmpty())
        val inputArray = params[0]

        if (inputArray != null && inputArray.size >= 2) {
            // calculate average
            val avgTime = inputArray.map { it.second }.average()
            publishProgress(avgTime)
            if (isCancelled || deviationDataHolder == null) return null

            // calculate variance
            var sum = 0.0
            inputArray.forEach { sum += (it.second.toDouble() - avgTime).pow(2) }

            // return deviation
            // sum is based on inputArray.size elements. The formula takes this amount -1. so we got inputArray.size -1
            return sqrt((sum / (inputArray.size - 1)))
        } else {
            publishProgress(null)
            return null
        }
    }
}