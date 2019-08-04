package com.pascaldornfeld.gsdble.overview.components

import com.pascaldornfeld.gsdble.overview.fragments.GraphFragment
import kotlin.math.pow
import kotlin.math.sqrt

class CharaDataStatsByGradient(
    timestamp: Long,
    averageGraph: GraphFragment<Float>?,
    deviationGraph: GraphFragment<Float>?
) : CharaDataStatsCalculatorAsyncTask(timestamp, averageGraph, deviationGraph) {

    override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Float? {
        assert(params.isNotEmpty())
        val inputArray = params[0]

        if (inputArray != null && inputArray.size >= 3) {
            // calculate average
            val avgTime = inputArray.mapIndexed { index, pair ->
                if (isCancelled) return null
                if (index == 0) null
                else {
                    val prev = inputArray[index - 1]
                    (pair.second.toDouble() - prev.second.toDouble()) / (pair.first.toDouble() - prev.first.toDouble())
                }
            }.filterNotNull().average()
            publishProgress(avgTime.toFloat())

            // calculate variance
            var sum = 0.0f
            inputArray.forEachIndexed { index, pair ->
                if (index != 0) {
                    val prev = inputArray[index - 1]
                    val gradient =
                        (pair.second.toDouble() - prev.second.toDouble()) / (pair.first.toDouble() - prev.first.toDouble())
                    sum += (gradient - avgTime).pow(2).toFloat()
                }
                if (isCancelled) return null
            }

            // return deviation
            // sum is based on inputArray.size -1 elements. The formula takes this amount -1. so we got inputArray.size -2
            return sqrt((sum / (inputArray.size - 2).toFloat()))
        } else {
            publishProgress(null)
            return null
        }
    }
}