package com.onenotes.app.ui.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.databinding.ActivityScanBinding
import com.onenotes.app.util.contentUriFor
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Document scanner.
 *
 * The picture is taken by the camera app of the device (no camera permission is needed and the
 * result is the same Samsung / Google camera the user already knows), then it can be straightened,
 * turned into black & white and finally read by the on device OCR of ML Kit: the recognised text
 * is returned to the editor together with the image, so that a scanned page becomes a searchable
 * note.
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val container by lazy { OneNotesApp.containerOf(this) }

    private var source: Bitmap? = null
    private var rotation = 0
    private var filter = Filter.ORIGINAL
    private var recognizedText = ""

    private var cameraFile: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) loadPicture() else finish()
    }

    private enum class Filter { ORIGINAL, GRAYSCALE, BLACK_WHITE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scanClose.setOnClickListener { finish() }
        binding.scanSave.setOnClickListener { save() }
        binding.scanRotate.setOnClickListener { rotate() }
        binding.scanFilterOriginal.setOnClickListener { applyFilter(Filter.ORIGINAL) }
        binding.scanFilterGrayscale.setOnClickListener { applyFilter(Filter.GRAYSCALE) }
        binding.scanFilterBw.setOnClickListener { applyFilter(Filter.BLACK_WHITE) }
        binding.scanOcr.setOnClickListener { recognizeText() }
        applyFilter(Filter.ORIGINAL)

        val dir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        cameraFile = file
        val uri = runCatching { contentUriFor(file) }.getOrNull()
        if (uri == null) {
            toast(R.string.msg_error_camera)
            finish()
            return
        }
        runCatching { takePicture.launch(uri) }
            .onFailure {
                toast(R.string.msg_error_camera)
                finish()
            }
    }

    private fun loadPicture() {
        val file = cameraFile ?: return finish()
        if (!file.exists() || file.length() == 0L) {
            toast(R.string.msg_error_camera)
            finish()
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            toast(R.string.msg_error_camera)
            finish()
            return
        }
        source = bitmap
        render()
    }

    private fun rotate() {
        rotation = (rotation + 90) % 360
        render()
    }

    private fun applyFilter(value: Filter) {
        filter = value
        val selected = ContextCompat.getColor(this, R.color.onenotes_primary)
        val normal = ContextCompat.getColor(this, R.color.onenotes_text_primary)
        binding.scanFilterOriginal.setTextColor(if (filter == Filter.ORIGINAL) selected else normal)
        binding.scanFilterGrayscale.setTextColor(if (filter == Filter.GRAYSCALE) selected else normal)
        binding.scanFilterBw.setTextColor(if (filter == Filter.BLACK_WHITE) selected else normal)
        render()
    }

    private fun render() {
        val original = source ?: return
        val rotated = if (rotation == 0) original else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        }
        binding.scanPreview.setImageBitmap(filtered(rotated))
    }

    private fun filtered(bitmap: Bitmap): Bitmap {
        if (filter == Filter.ORIGINAL) return bitmap
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (filter == Filter.GRAYSCALE) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        } else {
            // Adaptive-ish threshold: the saturation is removed first, then the contrast is
            // pushed until the page becomes pure black & white.
            val matrix = ColorMatrix(
                floatArrayOf(
                    0.33f, 0.33f, 0.33f, 0f, -40f,
                    0.33f, 0.33f, 0.33f, 0f, -40f,
                    0.33f, 0.33f, 0.33f, 0f, -40f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val contrast = ColorMatrix(
                floatArrayOf(
                    4f, 0f, 0f, 0f, 0f,
                    0f, 4f, 0f, 0f, 0f,
                    0f, 0f, 4f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(contrast)
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun recognizeText() {
        val bitmap = currentBitmap() ?: return
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                recognizedText = result.text
                binding.scanText.text = recognizedText
                binding.scanText.visibility = View.VISIBLE
                toast(R.string.msg_ocr_done)
            }
            .addOnFailureListener { toast(R.string.msg_error_generic) }
    }

    private fun currentBitmap(): Bitmap? {
        val original = source ?: return null
        val rotated = if (rotation == 0) original else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        }
        return filtered(rotated)
    }

    private fun save() {
        val bitmap = currentBitmap()
        if (bitmap == null) {
            toast(R.string.msg_error_camera)
            return
        }
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                val bytes = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                    stream.toByteArray()
                }
                val fileName = container.fileStorage.newAttachmentName("jpg")
                container.fileStorage.write(fileName, bytes)
                fileName
            }
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(RESULT_FILE, name)
                    .putExtra(RESULT_TEXT, recognizedText)
            )
            toast(R.string.scan_saved)
            finish()
        }
    }

    override fun onDestroy() {
        cameraFile?.delete()
        super.onDestroy()
    }

    companion object {
        const val RESULT_FILE = "result_file"
        const val RESULT_TEXT = "result_text"

        fun intent(context: Context): Intent = Intent(context, ScanActivity::class.java)
    }
}
