package com.example.anomalydetection

import AnomalyDetector
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.opencsv.CSVReader
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var anomalyDetector: AnomalyDetector
    private lateinit var resultTable: TableLayout
    private lateinit var pieChart: PieChart
    private val PICK_CSV_REQUEST_CODE = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Initialize UI elements
        anomalyDetector = AnomalyDetector(this)
        resultTable = findViewById(R.id.resultTable)
        pieChart = findViewById(R.id.pieChart)

        checkStoragePermissions()
        // Set up the file picker button
        val pickFileButton: Button = findViewById(R.id.pickFileButton)
        pickFileButton.setOnClickListener {
            pickCSVFile()
        }

    }

    // Opens file picker to select a CSV file
    private fun pickCSVFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_CSV_REQUEST_CODE)
    }

    // Handle selected CSV file
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CSV_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                processCSVFile(uri)
            }
        }
    }

    // Process the CSV file and perform anomaly detection
    private fun processCSVFile(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Parse the CSV and detect anomalies
            var fraudCount = 0
            var normalCount = 0

            reader.readLine() // Skip the header
            reader.forEachLine { line ->
                val values = line.split(",").map { it.toFloat() }.toFloatArray()

                // Perform anomaly detection
                val isAnomaly = anomalyDetector.detectAnomalies(values)
                addResultToTable(values[28], isAnomaly)

                // Count results for visualization
                if (isAnomaly) fraudCount++ else normalCount++
            }

            // Update PieChart with the results
            updatePieChart(fraudCount, normalCount)
            resultTable.visibility = View.VISIBLE
            pieChart.visibility = View.VISIBLE

            Toast.makeText(this, "CSV processed successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing CSV file", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Add each result to the TableLayout
    private fun addResultToTable(amount: Float, isAnomaly: Boolean) {
        val row = TableRow(this)
        val amountText = TextView(this).apply { text = "$amount" }
        val statusText = TextView(this).apply { text = if (isAnomaly) "Fraud" else "Normal" }

        row.addView(amountText)
        row.addView(statusText)
        resultTable.addView(row)
    }

    // Update PieChart with anomaly and normal counts
    private fun updatePieChart(fraudCount: Int, normalCount: Int) {
        val entries = listOf(
            PieEntry(fraudCount.toFloat(), "Fraud"),
            PieEntry(normalCount.toFloat(), "Normal")
        )
        val dataSet = PieDataSet(entries, "Transaction Results")
        dataSet.colors = listOf(
            Color.RED,   // Color for Fraud
            Color.GREEN  // Color for Normal
        )
        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false // Remove description label
        pieChart.isDrawHoleEnabled = true      // Enable hole in the center
        pieChart.holeRadius = 45f              // Set the radius of the hole
        pieChart.setTransparentCircleRadius(50f)
        pieChart.setUsePercentValues(true)     // Display percentages
        pieChart.setEntryLabelTextSize(12f)
        pieChart.setBackgroundColor(Color.TRANSPARENT)
        pieChart.invalidate() // Refresh chart
    }
    private fun checkStoragePermissions() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        anomalyDetector.close()
    }

}
