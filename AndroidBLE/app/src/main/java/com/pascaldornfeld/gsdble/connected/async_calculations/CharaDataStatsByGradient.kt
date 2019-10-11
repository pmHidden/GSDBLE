package com.pascaldornfeld.gsdble.connected.async_calculations

import kotlin.math.pow
import kotlin.math.sqrt

class CharaDataStatsByGradient(
    averageFunction: ((Double?) -> Unit)?,
    private val deviationFunction: ((Double?) -> Unit)?
) : CharaDataStatsCalculatorAsyncTask(averageFunction, deviationFunction) {

    override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Double? {
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
            publishProgress(avgTime)

            return if (isCancelled || deviationFunction == null) null
            else {
                // calculate variance
                var sum = 0.0
                inputArray.forEachIndexed { index, pair ->
                    if (index != 0) {
                        val prev = inputArray[index - 1]
                        val gradient =
                            (pair.second.toDouble() - prev.second.toDouble()) / (pair.first.toDouble() - prev.first.toDouble())
                        sum += (gradient - avgTime).pow(2)
                    }
                    if (isCancelled) return null
                }

                // return deviation
                // sum is based on inputArray.size -1 elements. The formula takes this amount -1. so we got inputArray.size -2
                sqrt((sum / (inputArray.size - 2)))
            }
        } else {
            publishProgress(null)
            return null
        }
    }
}