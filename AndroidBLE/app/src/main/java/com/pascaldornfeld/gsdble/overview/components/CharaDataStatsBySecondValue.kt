package com.pascaldornfeld.gsdble.overview.components

import com.pascaldornfeld.gsdble.overview.fragments.GraphFragment
import kotlin.math.pow
import kotlin.math.sqrt

class CharaDataStatsBySecondValue(
    timestamp: Long,
    averageGraph: GraphFragment<Double>?,
    deviationGraph: GraphFragment<Double>?
) : CharaDataStatsCalculatorAsyncTask(timestamp, averageGraph, deviationGraph) {

    override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Double? {
        assert(params.isNotEmpty())
        val inputArray = params[0]

        if (inputArray != null && inputArray.size >= 2) {
            // calculate average
            val avgTime = inputArray.map { it.second }.average()
            publishProgress(avgTime)
            if (isCancelled) return null

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