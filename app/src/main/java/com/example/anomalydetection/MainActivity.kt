package com.example.anomalydetection

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.opencsv.CSVReader
import org.tensorflow.lite.Interpreter
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var resultTable: TableLayout
    private lateinit var pieChart: PieChart
    private val PICK_CSV_FILE = 1

    private var fraudCount = 0
    private var nonFraudCount = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the UI elements
        val pickFileButton: Button = findViewById(R.id.pickFileButton)
        resultTable = findViewById(R.id.resultTable)
        pieChart = findViewById(R.id.pieChart)

        // Load the TensorFlow Lite model from the assets folder
        val tfliteModel = loadModelFile()
        interpreter = Interpreter(tfliteModel)

        // Set up the button to pick the CSV file
        pickFileButton.setOnClickListener {
            // Clear previous results
            clearTable()
            fraudCount = 0
            nonFraudCount = 0
            pickCSVFile()
        }
    }
    private fun updateChart() {
        if (fraudCount == 0 && nonFraudCount == 0) {
            pieChart.visibility = View.GONE
            return
        }
        val entries = listOf(
            PieEntry(fraudCount.toFloat(), "Fraud"),
            PieEntry(nonFraudCount.toFloat(), "Non-Fraud")
        )
        val dataSet = PieDataSet(entries, "Fraud Detection")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        val data = PieData(dataSet)

        pieChart.data = data
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setDrawEntryLabels(true)
        pieChart.legend.isEnabled = true
        pieChart.invalidate() // Refresh chart

        pieChart.visibility = View.VISIBLE
    }

    // Function to allow the user to pick a CSV file
    private fun pickCSVFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "text/csv"
        startActivityForResult(intent, PICK_CSV_FILE)
    }

    // Handle the file selection result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_CSV_FILE && resultCode == Activity.RESULT_OK) {
            val csvUri: Uri? = data?.data
            csvUri?.let {
                processCSVFile(it)
            }
        }
    }

    // Load the TensorFlow Lite model from the assets folder
    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = assets.openFd("fraud_detection_model_v2.tflite")
        val fileInputStream = assetFileDescriptor.createInputStream()
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Clear previous table rows
    private fun clearTable() {
        // Keep the header row (index 0), clear others
        resultTable.removeViews(1, resultTable.childCount - 1)
    }

    // Process the CSV file and perform predictions
    private fun processCSVFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = CSVReader(InputStreamReader(inputStream))
            val csvData = reader.readAll()

            Log.d("CSVData", "CSV Rows: ${csvData.size}")

            if (csvData.isNotEmpty()) {
                csvData.removeAt(0)
                resultTable.visibility = View.VISIBLE

                for (row in csvData) {
                    Log.d("CSVRow", "Row: ${row.joinToString(", ")}")

                    if (row.size < 29) {
                        Log.e("CSVRow", "Row does not have enough columns: ${row.joinToString(", ")}")
                        continue
                    }

                    val inputBuffer = preprocessInput(row)
                    val outputData = Array(1) { FloatArray(1) }

                    interpreter.run(inputBuffer, outputData)
                    Log.d("ModelOutput", "Raw output: ${outputData[0][0]}")


                    val fraudStatus = if (outputData[0][0] > 1) "Fraud" else "Non-Fraud"
                    val amount = row.last()
                    if (fraudStatus == "Fraud") fraudCount++ else nonFraudCount++

                    runOnUiThread {
                        addRowToTable(amount, fraudStatus)
                    }
                }
                updateChart()
            }
        } catch (e: Exception) {
            Log.e("CSVError", "Error processing CSV: ${e.message}")
            e.printStackTrace()
        }
    }


    // Preprocess CSV row data before running inference
    private fun preprocessInput(row: Array<String>): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(29 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (i in 0..28) {
            val value = row[i].toFloat()
            inputBuffer.putFloat(value)
        }

        return inputBuffer
    }

    // Add a row to the result table
    private fun addRowToTable(amount: String, fraudStatus: String) {
        val newRow = TableRow(this)

        val amountTextView = TextView(this)
        amountTextView.text = amount
        amountTextView.setPadding(8, 8, 8, 8)

        val statusTextView = TextView(this)
        statusTextView.text = fraudStatus
        statusTextView.setPadding(8, 8, 8, 8)

        newRow.addView(amountTextView)
        newRow.addView(statusTextView)

        resultTable.addView(newRow)
    }
}
