package com.pascaldornfeld.gsdble.overview.components

import com.pascaldornfeld.gsdble.overview.fragments.GraphFragment
import kotlin.math.pow
import kotlin.math.sqrt

class CharaDataStatsBySecondValue(
    timestamp: Long,
    averageGraph: GraphFragment<Float>?,
    deviationGraph: GraphFragment<Float>?
) : CharaDataStatsCalculatorAsyncTask(timestamp, averageGraph, deviationGraph) {

    override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Float? {
        assert(params.isNotEmpty())
        val inputArray = params[0]

        if (inputArray != null && inputArray.size >= 2) {
            // calculate average
            val avgTime = inputArray.map { it.second }.average()
            publishProgress(avgTime.toFloat())
            if (isCancelled) return null

            // calculate variance
            var sum = 0.0f
            inputArray.forEach { sum += (it.second - avgTime).pow(2).toFloat() }

            // return deviation
            // sum is based on inputArray.size elements. The formula takes this amount -1. so we got inputArray.size -1
            return sqrt((sum / (inputArray.size - 1).toFloat()))
        } else {
            publishProgress(null)
            return null
        }
    }
}