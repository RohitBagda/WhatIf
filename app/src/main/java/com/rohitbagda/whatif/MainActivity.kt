package com.rohitbagda.whatif

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rohitbagda.whatif.MainActivity.Companion.CLEAR_DISK
import com.rohitbagda.whatif.MainActivity.Companion.FILL_DISK
import com.rohitbagda.whatif.MainActivity.Companion.MB_100
import com.rohitbagda.whatif.ui.theme.WhatIfTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

class MainActivity : ComponentActivity() {

    companion object {
        const val MB_100 = 104857600
        const val FILL_DISK = "Fill Disk"
        const val CLEAR_DISK = "Clear Disk"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        setContent {
            WhatIfTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                   FillDisk(ctx)
                }
            }
        }
    }

}

@Composable
fun FillDisk(ctx: Context) {
    val storageInfo = StorageInfo(
        totalDiskSpace = DiskSpaceCalculator.getTotalInternalMemorySize(),
        availableDiskSpace = DiskSpaceCalculator.getAvailableInternalMemorySize(),
        tempFileSize = 0
    )
    val buttonTextState = remember { mutableStateOf(FILL_DISK) }
    val storageDescriptionState = remember { mutableStateOf(storageInfo.toString()) }
    val filePath = Files.createTempFile(ctx.cacheDir.toPath(), "WhatIf", ".tmp")
    val coroutineScope = rememberCoroutineScope()
    WhatIfTheme {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = storageDescriptionState.value)
            Button(onClick = {
                when (buttonTextState.value) {
                    FILL_DISK -> fillDisk(coroutineScope, storageInfo, filePath, storageDescriptionState, buttonTextState)
                    else -> clearDisk(coroutineScope, filePath, storageInfo, storageDescriptionState, buttonTextState)
                }
            }) {
                Text(text = buttonTextState.value)
            }
        }
    }
} // Dispatchers.Main

private fun fillDisk(
    coroutineScope: CoroutineScope,
    storageInfo: StorageInfo,
    filePath: Path,
    descriptionState: MutableState<String>,
    buttonTextState: MutableState<String>
) {
    coroutineScope.launch {
        async {
            val buffer = MB_100 // storage space to leave on device
            storageInfo.availableDiskSpace = DiskSpaceCalculator.getAvailableInternalMemorySize()
            descriptionState.value = storageInfo.toString()
            val preferredTempFileSize =
                when {
                    storageInfo.availableDiskSpace < buffer -> {
                        println("Not enough disk space available - try clearing your disk space!")
                        return@async
                    }
                    else -> storageInfo.availableDiskSpace - buffer
                }

            // Assumes the phone has atleast 100 MB of available heap memory for use.
            val chunkSize = MB_100
            val chunks = (preferredTempFileSize / chunkSize).toInt()
            val remainder = (preferredTempFileSize % chunkSize).toInt()

            println("Available Disk Space: ${storageInfo.availableDiskSpace}, PreferredTempFileSize: $preferredTempFileSize, Chunk Size: $chunkSize, Chunks: $chunks, Remainder: $remainder")
            val fos = FileOutputStream(File(filePath.toUri()))
            repeat(chunks) {
                fos.write(ByteArray(chunkSize))
                println("Writing byte chunk #$it")
            }.also {
                println("Writing Remainder")
                fos.write(ByteArray(remainder))
                fos.close()
            }
            println("Finished Writing temp file to system")
            storageInfo.availableDiskSpace = DiskSpaceCalculator.getAvailableInternalMemorySize()
            storageInfo.tempFileSize = File(filePath.toUri()).length()
            println("Temp file size: ${storageInfo.tempFileSize}")
            println("Remaining Disk Space: ${storageInfo.availableDiskSpace}")
            descriptionState.value = storageInfo.toString()
            buttonTextState.value = CLEAR_DISK
        }.await()
    }
}

private fun clearDisk(
    coroutineScope: CoroutineScope,
    filePath: Path,
    storageInfo: StorageInfo,
    descriptionState: MutableState<String>,
    buttonTextState: MutableState<String>
) {
    coroutineScope.launch {
        println("Disk Space Available before delete: ${DiskSpaceCalculator.getAvailableInternalMemorySize()}")
        Files.deleteIfExists(filePath)
        storageInfo.availableDiskSpace = DiskSpaceCalculator.getAvailableInternalMemorySize()
        println("Disk Space Available post delete: ${storageInfo.availableDiskSpace}")
        storageInfo.tempFileSize = File(filePath.toUri()).length()
        descriptionState.value = storageInfo.toString()
        buttonTextState.value = FILL_DISK
    }
}

data class StorageInfo(
    var totalDiskSpace: Long,
    var availableDiskSpace: Long,
    var tempFileSize: Long,
) {
    override fun toString(): String {
        return "Total Disk Space=$totalDiskSpace\nAvailable DiskS pace=$availableDiskSpace\nTemp File Size=$tempFileSize\n"
    }
}