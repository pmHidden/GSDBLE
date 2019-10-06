package com.pascaldornfeld.gsdble.connected.async_calculations

import android.os.AsyncTask
import com.pascaldornfeld.gsdble.connected.SmallDataHolder

/**
 * creates the average and deviation of given data
 *
 * input:
 * Array<Pair<Long,Long>>? Data, or null to clear graph
 *
 * progress:
 * Long? Calculated Average Of Data, Or Null To Clear
 *
 * result:
 * Float? Calculated Deviation Of Data, Or Null To Clear
 */
abstract class CharaDataStatsCalculatorAsyncTask(
    private val timestamp: Long,
    private val averageDataHolder: SmallDataHolder<Double>?,
    private val deviationDataHolder: SmallDataHolder<Double>?
) : AsyncTask<Array<Pair<Long, Long>>?, Double?, Double?>() {

    abstract override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Double?

    override fun onProgressUpdate(vararg values: Double?) {
        super.onProgressUpdate(*values)
        assert(values.isNotEmpty())

        val average = values[0]
        if (average == null) averageDataHolder?.clearData()
        else averageDataHolder?.addData(timestamp, average)
    }

    override fun onPostExecute(result: Double?) {
        super.onPostExecute(result)
        if (result != null) deviationDataHolder?.addData(timestamp, result)
        else deviationDataHolder?.clearData()
    }
}