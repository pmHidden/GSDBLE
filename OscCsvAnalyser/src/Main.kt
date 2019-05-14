import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun main() {
    println("Set Path: ")
    var path: String? = null
    Scanner(System.`in`).use { path = it.next() }
    assertNotNull(path, "count not read path from console")
    val files = File(path).listFiles { _, name -> name.endsWith(".csv") }

    // globals
    var setup: Array<Short>? = null
    var integralTimes2 = 0L
    var totalTime = 0L
    val lockObjectSetup = Object()
    val lockObjectServe = Object()
    val countdown = CountDownLatch(files.size)

    // calc values of every file on new threads and add results to globals
    files.forEach { file ->
        Thread {
            var fileIntegralTimes2 = 0L
            var fileTime = 0L
            file.bufferedReader().use { reader ->
                var lastTimestamp: Long?
                var lastDiff: Long?

                while (true) {
                    // setup with first element
                    val firstSplitted = (reader.readLine()!!).split(',')
                    if (firstSplitted.size < 3) continue // need at least 3 values: Time, A and B
                    val thisFileSetup = arrayOf(
                        afterComma(firstSplitted[0]),
                        afterComma(firstSplitted[1]),
                        afterComma(firstSplitted[2])
                    )
                    if (thisFileSetup.any { it == (-1).toShort() }) continue // one of the values is not a decimal
                    // set or check setup-array
                    synchronized(lockObjectSetup) {
                        if (setup == null) {
                            assertEquals(thisFileSetup[1], thisFileSetup[2], "comma position of A and B is different")
                            setup = thisFileSetup
                        } else assertTrue(Arrays.deepEquals(setup, thisFileSetup), "setup arrays are not equal")
                    }
                    lastTimestamp = parseIt(firstSplitted, 0, setup!!)
                    lastDiff = (parseIt(firstSplitted, 1, setup!!) - parseIt(firstSplitted, 2, setup!!))
                    break
                }

                while (true) {
                    val currentSplitted = (reader.readLine() ?: break).split(',')
                    val currentTime = parseIt(currentSplitted, 0, setup!!)
                    val currentDiff =
                        (parseIt(currentSplitted, 1, setup!!) - parseIt(currentSplitted, 2, setup!!)) // trapeze a

                    val timeDifference = Math.subtractExact(currentTime, lastTimestamp!!) // trapeze height
                    val sumAB = Math.addExact(lastDiff!!, currentDiff)
                    val areaTimes2 = Math.multiplyExact(timeDifference, sumAB) // trapeze area *2
                    fileIntegralTimes2 = Math.addExact(fileIntegralTimes2, areaTimes2)
                    fileTime = Math.addExact(fileTime, timeDifference)

                    lastTimestamp = currentTime
                    lastDiff = currentDiff
                }
            }
            println("finished a file.")
            synchronized(lockObjectServe) {
                integralTimes2 = Math.addExact(integralTimes2, fileIntegralTimes2)
                totalTime = Math.addExact(totalTime, fileTime)
            }
            countdown.countDown()
        }.start()
    }
    countdown.await()
    // print end results
    println("finished all.")
    println("integral: " + (integralTimes2 / 2L))
    println("total time: $totalTime")
    println("setup was: ${Arrays.toString(setup)}")
}

fun parseIt(splitted: List<String>, index: Int, setup: Array<Short>): Long {
    assertEquals(setup[index], afterComma(splitted[index]), "different position of comma")
    return splitted[index].replaceFirst(".", "").toLong()
}

fun afterComma(string: String): Short {
    val index = string.indexOf('.')
    return if (index == -1) (-1).toShort()
    else (string.length - index - 1).toShort()
}