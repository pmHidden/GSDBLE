import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals

fun main(args: Array<String>) {
    println("Set Path: ")
    val inputScanner = Scanner(System.`in`)
    val path = inputScanner.next()
    val folder = File(path)
    val files = folder.listFiles { _, name -> name.endsWith(".csv") }
    val integral = AtomicLong(0)
    val numberOfEntries = AtomicLong(0)
    var timesteps: Long? = null
    var setup: Array<Short>? = null

    val lockObjectSetup = Object()
    val lockObjectTimestamp = Object()
    val countdown = CountDownLatch(files.size)

    // for every file:
    files.forEach { file ->
        Thread {
            file.bufferedReader().use { reader ->
                var fileIntegral = 0L
                var fileEntries = 0L
                val fileTimestamp: Long? // constant after init. same as global timesteps
                var lastTimestamp: Long?

                while (true) {
                    val firstAsString: String = reader.readLine()!!
                    // setup comma-checker
                    val firstSplitted = firstAsString.split(',')
                    if (firstSplitted.size < 3) continue
                    val thisFileSetup = arrayOf(
                        afterComma(firstSplitted[0]),
                        afterComma(firstSplitted[1]),
                        afterComma(firstSplitted[2])
                    )
                    if (thisFileSetup.any { it == (-1).toShort() }) continue
                    // set or check setup-array
                    synchronized(lockObjectSetup) {
                        if (setup == null) {
                            assertEquals(thisFileSetup[1], thisFileSetup[2])
                            setup = thisFileSetup
                        } else assert(Arrays.deepEquals(setup, thisFileSetup))
                    }
                    fileIntegral += (parseIt(firstSplitted, 1, setup!!) - parseIt(firstSplitted, 2, setup!!))
                    fileEntries++

                    val secondAsString = reader.readLine()!!
                    val secondSplitted = secondAsString.split(',')
                    fileIntegral += (parseIt(secondSplitted, 1, setup!!) - parseIt(secondSplitted, 2, setup!!))
                    fileEntries++

                    lastTimestamp = parseIt(secondSplitted, 0, setup!!)
                    fileTimestamp = lastTimestamp - parseIt(firstSplitted, 0, setup!!)
                    synchronized(lockObjectTimestamp) {
                        if (timesteps == null) timesteps = fileTimestamp else assertEquals(timesteps, fileTimestamp)
                    }

                    break
                }

                while (true) {
                    val currentAsString: String = reader.readLine() ?: break

                    val currentSplitted = currentAsString.split(',')
                    val currentTime = parseIt(currentSplitted, 0, setup!!)
                    //assertEquals(currentTime - lastTimestamp!!, fileTimestamp)

                    fileIntegral += (parseIt(currentSplitted, 1, setup!!) - parseIt(currentSplitted, 2, setup!!))
                    fileEntries++

                    lastTimestamp = currentTime
                }
                integral.addAndGet(fileIntegral)
                numberOfEntries.addAndGet(fileEntries)
                println("finished a file. my integral : $fileIntegral after $fileEntries entries")
            }
            countdown.countDown()
        }.start()
    }

    countdown.await()
    println("finished all. integral : ${integral.get()} after ${numberOfEntries.get()} entries")
    print("timestep was: $timesteps and setup was: ${Arrays.toString(setup)}")
}

fun parseIt(splitted: List<String>, index: Int, setup: Array<Short>): Long {
    assertEquals(afterComma(splitted[index]), setup[index])
    return splitted[index].replaceFirst(".", "").toLong()
}

fun afterComma(string: String): Short {
    val index = string.indexOf('.')
    return if (index == -1) (-1).toShort()
    else (string.length - index - 1).toShort()
}