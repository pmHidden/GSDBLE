package com.pascaldornfeld.gsdble.overview.components

import android.os.AsyncTask
import com.pascaldornfeld.gsdble.overview.fragments.GraphFragment
import java.lang.ref.WeakReference

/**
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
    averageGraph: GraphFragment<Float>?,
    deviationGraph: GraphFragment<Float>?
) : AsyncTask<Array<Pair<Long, Long>>?, Float?, Float?>() {
    private val averageGraphRef = WeakReference(averageGraph)
    private val deviationGraphRef = WeakReference(deviationGraph)

    abstract override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Float?

    override fun onProgressUpdate(vararg values: Float?) {
        super.onProgressUpdate(*values)
        assert(values.isNotEmpty())

        val average = values[0]
        val graph = averageGraphRef.get()
        if (average == null) graph?.internalData?.clear()
        else graph?.addData(timestamp, average)
    }

    override fun onPostExecute(result: Float?) {
        super.onPostExecute(result)
        val graph = deviationGraphRef.get()
        if (result != null) graph?.addData(timestamp, result) else graph?.internalData?.clear()
    }
}