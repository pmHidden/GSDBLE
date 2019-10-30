package com.pascaldornfeld.gsdble.file_dumping

import android.content.Context
import android.os.AsyncTask
import android.os.Environment
import android.provider.Settings.Secure
import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter


object FileOperations {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Write raw and meta data of a gesture to a JSON file
     * and save it in the user's directory in background.
     *
     * @param context     Training fragment context
     * @param username        The user who executed to gesture
     * @param gestureData The class which contains all raw and meta data of the gesture
     */
    fun writeGestureFile(context: Context, username: String, gestureData: GestureData) {
        AsyncTask.execute {
            gestureData.apply {
                if (time == null) setTimeToNow()
                user = username
                deviceId = Secure.getString(context.contentResolver, Secure.ANDROID_ID)
            }
            synchronized(FileOperations) {
                var fileNamePostfix = 2
                var file = getFileFromPrefixAndCreateParent(username, gestureData.time.toString())
                while (file.exists()) {
                    file = getFileFromPrefixAndCreateParent(
                        username,
                        "${gestureData.time.toString()}_$fileNamePostfix"
                    )
                    fileNamePostfix++
                }
                file
            }.let { file: File ->
                FileWriter(file, false).use { fileWriter: FileWriter ->
                    BufferedWriter(fileWriter).use { bufferedWriter: BufferedWriter ->
                        bufferedWriter.write(gson.toJson(gestureData))
                        bufferedWriter.newLine()
                    }
                }
            }
        }
    }

    /**
     * Return the number of already saved gesture files in the user's directory.
     *
     * @param user The current user name
     * @return Number of existing gesture files
     */
    private fun getNewFileCount(user: String): Int =
        File("${getGesturesFolderPath()}$user").listFiles()?.size ?: 0


    /**
     * Check if the desired directory path exists, if not it is created.
     *
     * @param dir      This directory name equals the user name
     * @param prefix The file name consists of data and time of the gesture
     * @return The file which will be written to.
     */
    private fun getFileFromPrefixAndCreateParent(dir: String, prefix: String): File =
        File("${getGesturesFolderPath()}$dir${File.separator}$prefix.json")
            .apply { if (!parentFile.exists()) parentFile.mkdirs() }

    private fun getGesturesFolderPath(): String =
        "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}Gestures${File.separator}"
}