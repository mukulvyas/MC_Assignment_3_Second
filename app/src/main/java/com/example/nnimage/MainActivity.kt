package com.example.nnimage

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


import android.Manifest // Add this import
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.nnimage.Screen.DisplayResult
import com.example.nnimage.ui.theme.NNImageTheme
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    private val REQUEST_PERMISSIONS = 1
    private lateinit var tflite: Interpreter
    private var inferenceResult by mutableStateOf<String>("")
    private var imageBitmap by mutableStateOf<Bitmap?>(null)
    private var probabilities by mutableStateOf<List<Float>>(emptyList())
    private var maxProbIndex by mutableStateOf(-1)
    private val startCamera =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                imageBitmap = result.data?.extras?.get("data") as Bitmap
                runInference()

            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedImage: Uri? = result.data?.data
                imageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImage)
                runInference()

            }
        }


    @SuppressLint("QueryPermissionsNeeded")

    fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            startCamera.launch(takePictureIntent)
        }
    }

    @SuppressLint("QueryPermissionsNeeded")

    fun openGallery() {
        Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).also { pickImageIntent ->
            pickImage.launch(pickImageIntent)
        }
    }

    fun checkPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val storagePermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        if (cameraPermission != PackageManager.PERMISSION_GRANTED || storagePermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERMISSIONS
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Add this line
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permissions granted
                } else {
                    // Permissions denied
                }
                return
            }

            else -> {
                // Ignore all other requests
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            tflite = Interpreter(loadModelFile())
            Log.d("Model", "onCreate: Model loaded successfully")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        setContent {
            NNImageTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DisplayResult(
                        ::openCamera,
                        ::openGallery,
                        imageBitmap,
                        inferenceResult,
                        probabilities,
                        maxProbIndex
                    )


                }
            }
        }
    }

    private fun loadModelFile(): ByteBuffer {
        return try {
            val fileDescriptor = assets.openFd("FirstModel.tflite")
            val inputStream = fileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
        } catch (e: Exception) {
            Log.e("TFLite", "Error loading model", e)
            throw RuntimeException("Error loading model.", e)
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        return try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(224 * 224)
            resizedBitmap.getPixels(
                intValues,
                0,
                resizedBitmap.width,
                0,
                0,
                resizedBitmap.width,
                resizedBitmap.height
            )
            var pixel = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f)
                    byteBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)
                    byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)
                }
            }
            byteBuffer
        } catch (e: Exception) {
            Log.e("TFLite", "Error converting bitmap to ByteBuffer", e)
            throw RuntimeException("Error converting bitmap to ByteBuffer.", e)
        }
    }

    private fun runInference() {
        try {
            imageBitmap?.let { bitmap ->
                val byteBuffer = convertBitmapToByteBuffer(bitmap)
                val result =
                    Array(1) { FloatArray(9) } // Adjusted to match the model's output shape
                tflite.run(byteBuffer, result)

                // Process the result array here
                result[0].forEachIndexed { index, probability ->
                    Log.d("MainData", "Probability of class $index: $probability")
                }

                val maxProbIndexo = result[0].indices.maxByOrNull { result[0][it] } ?: -1
                // maxProbIndex is the index of the class with the highest probability
                // Log.d("MainData", "Class with highest probability: $maxProbIndex")
                //Text
                inferenceResult = "Class with highest probability: $maxProbIndexo"
                // Process the result array here
                probabilities = result[0].toList()

                maxProbIndex = result[0].indices.maxByOrNull { result[0][it] }!!
                // maxProbIndex is the index of the class with the highest probability

            }
        } catch (e: Exception) {
            Log.e("TFLite", "Error running inference", e)
            throw RuntimeException("Error running inference.", e)
        }
    }
}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
//@Composable
//fun DisplayResult(openCamera: () -> Unit, openGallery: () -> Unit, imageBitmap: Bitmap?, inferenceResult: String, probabilities: List<Float>, maxProbIndex: Int ) {
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                colors = TopAppBarDefaults.topAppBarColors(
//                containerColor = Color.LightGray),
//                title = { Text("NN Image") },
//                actions = {
//                    IconButton(onClick = { /* Handle click */ }) {
//                        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
//                    }
//                }
//            )
//        }
//    ) {
//        Column(modifier = Modifier
//            .fillMaxWidth()
//            .fillMaxHeight()
//
//        ) {
//
//            Box(modifier = Modifier.fillMaxWidth(),
//                contentAlignment = androidx.compose.ui.Alignment.Center) {
//                Button( modifier = Modifier.padding(top = 150.dp),
//
//                    onClick = { openCamera() }
//                ) {
//                    Text("Open Camera")
//                }
//
//                Button(
//
//                   onClick = { openGallery() }
//                ) {
//                    Text("Open Gallery")
//                }
//            }
//
//
//
//           CardList(
//                imageBitmap,inferenceResult,
//               probabilities,
//               maxProbIndex
//          )
//
//        }
//    }
//
//}
////fun CardList(imageBitmap: Bitmap?,inferenceResult: String, probabilities: List<Float>, maxProbIndex: Int)
//@Composable
//fun CardList(imageBitmap: Bitmap?,inferenceResult: String, probabilities: List<Float>, maxProbIndex: Int) {
//    val labels = listOf(
//        "Apple",
//        "Banana",
//        "Cherry",
//        "Chickoo",
//        "Grapes",
//        "Kiwi",
//        "Mango",
//        "Orange",
//        "Strawberry"
//    )
//
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .fillMaxHeight()
//            .padding(15.dp),
//
//        shape = RoundedCornerShape(corner = CornerSize(15.dp)),
//        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
//    )
//    {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth() // Fill the maximum width available
//                .weight(1f), // Take up half of the available space
//            horizontalArrangement = Arrangement.Center
//        ) {
//            imageBitmap?.let { bitmap ->
//                Image(
//                    painter = BitmapPainter(bitmap.asImageBitmap()),
//                    contentDescription = null,
//                    modifier = Modifier
//                        .fillMaxHeight()
//                        .weight(1f)
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.width(16.dp))
//
//        Column(
//            modifier = Modifier
//                .fillMaxWidth() // Fill the maximum width available
//                .weight(1f) // Take up half of the available space
//                .verticalScroll(rememberScrollState()),
//            verticalArrangement = Arrangement.Center,// Make the column scrollable
//        ) {
//            probabilities.mapIndexed { index, probability -> Pair(index, probability) }
//                .sortedByDescending { it.second }.take(3)
//                .windowed(2, 2, true) // Group predictions into pairs
//                .forEachIndexed { rowIndex, predictionPair ->
//                    Row { // Create a row for each pair of predictions
//                        predictionPair.forEachIndexed { predictionIndex, (index, probability) ->
//                            val percentage = probability * 100
//                            OutlinedTextField(
//                                value = "${labels[index]}: ${String.format("%.2f", percentage)}%",
//                                onValueChange = {},
//                                label = { Text("Prediction ${rowIndex * 2 + predictionIndex + 1}") },
//                                readOnly = true,
//                                modifier = Modifier
//                                    .weight(1f) // Each TextField takes up half of the available width
//                                    .padding(8.dp) // Add padding around the text field
//                            )
//                        }
//                    }
//                }
//
//            Row( modifier = Modifier,
//                horizontalArrangement = Arrangement.Center, // Center the content horizontally
//                verticalAlignment = Alignment.CenterVertically,
//                ){// Center the content vertically            ) { // Create a row for the fruit prediction
//                OutlinedTextField(
//                    value = if (maxProbIndex in labels.indices) labels[maxProbIndex] else "N/A",
//                    onValueChange = {},
//                    label = { Text("Fruit") },
//                    readOnly = true,
//                    modifier = Modifier
//                        .width(200.dp)
//                      //  .weight(1f) // The TextField takes up half of the available width
//                        .padding(8.dp) // Add padding around the text field
//                )
//                Spacer(Modifier.weight(1f)) // Add a spacer to fill the remaining space
//            }
//            Log.d("InferenceResult", "onCreate: $inferenceResult")
//            //Text(inferenceResult)
//        }
//    }
//
//}

