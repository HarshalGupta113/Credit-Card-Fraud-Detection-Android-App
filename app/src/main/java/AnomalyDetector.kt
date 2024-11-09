import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.log
import kotlin.math.pow

class AnomalyDetector (private val context: Context){
    private var interpreter: Interpreter? = null
    private val threshold = 0.5 // Replace with your actual threshold
    private val mean: FloatArray
    private val scale: FloatArray

    init {
        interpreter = Interpreter(loadModelFile())
        val scalerParams = loadScalerParams()
        mean = scalerParams.first
        scale = scalerParams.second
    }

    // Load the .tflite model
    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("autoencoder_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Load scaler parameters from JSON
    private fun loadScalerParams(): Pair<FloatArray, FloatArray> {
        val jsonString = context.assets.open("scaler_params.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val meanArray = jsonObject.getJSONArray("mean")
        val scaleArray = jsonObject.getJSONArray("scale")

        val mean = FloatArray(meanArray.length()) { meanArray.getDouble(it).toFloat() }
        val scale = FloatArray(scaleArray.length()) { scaleArray.getDouble(it).toFloat() }
        return Pair(mean, scale)
    }

    // Scale the input data
    private fun scaleInput(inputData: FloatArray): FloatArray {
        return FloatArray(inputData.size) { i ->
            (inputData[i] - mean[i]) / scale[i]
        }
    }

    // Detect anomalies
    fun detectAnomalies(inputData: FloatArray): Boolean {
        val scaledData = scaleInput(inputData)
        val inputArray = arrayOf(scaledData)

        // Prepare output array
        val outputArray = Array(1) { FloatArray(inputData.size) }

        // Run inference
        interpreter?.run(inputArray, outputArray)

        // Calculate reconstruction error
        val reconstructionError = calculateError(scaledData, outputArray[0])

        return reconstructionError > threshold
    }

    private fun calculateError(original: FloatArray, reconstructed: FloatArray): Float {
        var error = 0f
        for (i in original.indices) {
            error += (original[i] - reconstructed[i]).pow(2)
        }
        return error / original.size
    }

    fun close() {
        interpreter?.close()
    }
}