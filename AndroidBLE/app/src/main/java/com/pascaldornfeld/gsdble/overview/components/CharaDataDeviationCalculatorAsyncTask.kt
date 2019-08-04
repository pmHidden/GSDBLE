package com.pascaldornfeld.gsdble.overview.components

import android.os.AsyncTask
import com.pascaldornfeld.gsdble.overview.fragments.GraphFragment
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * input:
 * Long Current Time Second
 * Array<Pair<Long,Long>> Data
 * GraphFragment<Float> View To Push Result To
 *
 * progress:
 * not reported
 *
 * result:
 * Long  Current Time Second
 * Float Calculated Deviation Of Data Or Null
 * GraphFragment<Float> View To Push Result To
 */
class CharaDataDeviationCalculatorAsyncTask :
    AsyncTask<Triple<Long, Array<Pair<Long, Long>>, GraphFragment<Float>>, Void, Triple<Long, Float, GraphFragment<Float>>?>() {

    override fun doInBackground(vararg params: Triple<Long, Array<Pair<Long, Long>>, GraphFragment<Float>>?): Triple<Long, Float, GraphFragment<Float>>? {
        assert(params.isNotEmpty())
        val inputArray = params[0]!!.second

        if (inputArray.size >= 3) {
            // calculate average
            val avgTime = inputArray.mapIndexed { index, pair ->
                if (isCancelled) return null
                if (index == 0) null
                else {
                    val prev = inputArray[index - 1]
                    (pair.second.toDouble() - prev.second.toDouble()) / (pair.first.toDouble() - prev.first.toDouble())
                }
            }.filterNotNull().average()

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
            return Triple(params[0]!!.first, sqrt((sum / (inputArray.size - 2).toFloat())), params[0]!!.third)
        }
        return null
    }

    override fun onPostExecute(result: Triple<Long, Float, GraphFragment<Float>>?) {
        super.onPostExecute(result)
        result?.third?.addData(result.first, result.second)
    }
}