package com.example.nnimage.Screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun DisplayResult(openCamera: () -> Unit, openGallery: () -> Unit, imageBitmap: Bitmap?, inferenceResult: String, probabilities: List<Float>, maxProbIndex: Int ) {

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.LightGray),
                title = { Text("NN Image") },
                actions = {
                    IconButton(onClick = { /* Handle click */ }) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
                    }
                }
            )
        }
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()

        ) {

            Box(modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center) {
                Button( modifier = Modifier.padding(top = 150.dp),

                    onClick = { openCamera() }
                ) {
                    Text("Open Camera")
                }

                Button(

                    onClick = { openGallery() }
                ) {
                    Text("Open Gallery")
                }
            }



            CardList(
                imageBitmap,inferenceResult,
                probabilities,
                maxProbIndex
            )

        }
    }

}
//fun CardList(imageBitmap: Bitmap?,inferenceResult: String, probabilities: List<Float>, maxProbIndex: Int)
@Composable
fun CardList(imageBitmap: Bitmap?, inferenceResult: String, probabilities: List<Float>, maxProbIndex: Int) {
    val labels = listOf(
        "Apple",
        "Banana",
        "Cherry",
        "Chickoo",
        "Grapes",
        "Kiwi",
        "Mango",
        "Orange",
        "Strawberry"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(15.dp),

        shape = RoundedCornerShape(corner = CornerSize(15.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth() // Fill the maximum width available
                .weight(1f), // Take up half of the available space
            horizontalArrangement = Arrangement.Center
        ) {
            imageBitmap?.let { bitmap ->
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth() // Fill the maximum width available
                .weight(1f) // Take up half of the available space
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,// Make the column scrollable
        ) {
            probabilities.mapIndexed { index, probability -> Pair(index, probability) }
                .sortedByDescending { it.second }.take(3)
                .windowed(2, 2, true) // Group predictions into pairs
                .forEachIndexed { rowIndex, predictionPair ->
                    Row { // Create a row for each pair of predictions
                        predictionPair.forEachIndexed { predictionIndex, (index, probability) ->
                            val percentage = probability * 100
                            OutlinedTextField(
                                value = "${labels[index]}: ${String.format("%.2f", percentage)}%",
                                onValueChange = {},
                                label = { Text("Prediction ${rowIndex * 2 + predictionIndex + 1}") },
                                readOnly = true,
                                modifier = Modifier
                                    .weight(1f) // Each TextField takes up half of the available width
                                    .padding(8.dp) // Add padding around the text field
                            )
                        }
                    }
                }

            if (maxProbIndex in labels.indices) {
                Row( modifier = Modifier,
                    horizontalArrangement = Arrangement.Center, // Center the content horizontally
                    verticalAlignment = Alignment.CenterVertically,
                ){// Center the content vertically            ) { // Create a row for the fruit prediction
                    OutlinedTextField(
                        value = labels[maxProbIndex],
                        onValueChange = {},
                        label = { Text("Fruit") },
                        readOnly = true,
                        modifier = Modifier
                            .width(200.dp)
                            .padding(8.dp) // Add padding around the text field
                    )
                    Spacer(Modifier.weight(1f)) // Add a spacer to fill the remaining space
                }
            }
            Log.d("InferenceResult", "onCreate: $inferenceResult")
            //Text(inferenceResult)
        }
    }

}

