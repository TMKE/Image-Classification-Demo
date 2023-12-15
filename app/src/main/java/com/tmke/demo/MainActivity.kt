package com.tmke.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.tmke.demo.databinding.ActivityMainBinding
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
// import org.tensorflow.lite.support.image.TensorImage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var classifyButton: Button
    private lateinit var clearButton: Button
    private lateinit var tvOutput: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private val GALLERY_REQUEST_CODE = 123

    private lateinit var module: Module

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageview
        button = binding.captureButton
        classifyButton = binding.classifyButton
        clearButton = binding.clearButton
        tvOutput = binding.tvOutput
        progressIndicator = binding.progressIndicator
        val buttonLoad = binding.loadButton

        try {
            loadTorchModule("model_quantized.pt")
        } catch (e: IOException) {
            Log.e("TAG", "Error reading assets", e)
            finish()
        }

        button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        val classes = arrayOf<String>("airplane", "automobile", "bird", "cat", "deer", "dog", "frog",
        "horse", "ship", "truck")

        classifyButton.setOnClickListener {
            // TODO: Implement the logic of the classify button
            progressIndicator.setIndeterminate(true)
            val resized = Bitmap.createScaledBitmap(imageView.drawable.toBitmap(), 224, 224, true)
            val test = analyzeImage(resized)
            progressIndicator.setIndeterminate(false)
            progressIndicator.progress = 100
            progressIndicator.show()
            tvOutput.text = classes[test]
            Toast.makeText(this, "$test", Toast.LENGTH_SHORT)
            Log.i("TAG", "$test")

        }

        clearButton.setOnClickListener {
            changeButtonState(false)
        }

        buttonLoad.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    // request camera permission
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        if (granted) {
            takePicturePreview.launch(null)
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // launch the camera and take a picture
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            changeButtonState(true)
            classifyButton.isEnabled = true
            classifyButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.lime_green)
            classifyButton.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_blue))
            outputGenerator(bitmap)
        }
    }

    // get image from gallery
    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.i("TAG", "This is the result: ${result.data} ${result.resultCode}")
        onResultRecieved(GALLERY_REQUEST_CODE, result)
    }

    private fun onResultRecieved(requestCode: Int, result: ActivityResult?) {
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        classifyButton.isEnabled = true
                        classifyButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.lime_green)
                        classifyButton.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_blue))
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {

//        // declaring tensorflow lite model variable
//        val birdsModel = BirdsModel.newInstance(this)
//
//        // converting bitmap into tensorflow image
//        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//        val tfimage = TensorImage.fromBitmap(newBitmap)
//
//        // process the image using trained model and sort it in descending order
//        val outputs = birdsModel.process(tfimage)
//            .probabilityAsCategoryList.apply {
//                sortByDescending { it.score }
//            }
//
//        // getting result having high probability
//        val highProbabilityOutput = outputs[0]
//
//        // setting output text
//        tvOutput.text = highProbabilityOutput.label
//        Log.i("TAG", "outputGenerator: $highProbabilityOutput")
    }

    @SuppressLint("ResourceType")
    private fun changeButtonState(state: Boolean) {
        if (state) {
            classifyButton.isEnabled = true
            classifyButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.lime_green)
            classifyButton.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_blue))
        } else {
            classifyButton.isEnabled = false
            Log.i("TAG", "${getColorFromAttr(android.R.attr.colorAccent)}")
            Log.i("TAG", "${R.color.lime_green}")
            classifyButton.backgroundTintList = ContextCompat.getColorStateList(this, getColorFromAttr(R.attr.secondaryColor))
            classifyButton.setTextColor(ContextCompat.getColorStateList(this, getColorFromAttr(R.attr.secondaryTextColor)))
        }
    }

    fun Context.getColorFromAttr(
        attrColor: Int,
        typedValue: TypedValue = TypedValue(),
        resolveRefs: Boolean = true
    ): Int {
        theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }

    fun loadTorchModule(fileName: String) {
        // val modelFile = File(this.filesDir, fileName)
        module = Module.load(assetFilePath(this, fileName))
    }

    fun analyzeImage(bitmap: Bitmap): Int {
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap, TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
        TensorImageUtils.TORCHVISION_NORM_STD_RGB)

        // running the model
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()

        // getting tensor content as array of floats
        val scores = outputTensor.dataAsFloatArray

        // searching for the index with the maximum score
        var maxScore = -Float.MAX_VALUE
        var maxScoreIndex = -1
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxScoreIndex = i

            }
        }
        return maxScoreIndex
    }

    fun assetFilePath(context: Context, asset: String): String {
        val file = File(context.filesDir, asset)

        try {
            val inpStream: InputStream = context.assets.open(asset)
            try {
                val outStream = FileOutputStream(file, false)
                val buffer = ByteArray(4 * 1024)
                var read: Int

                while (true) {
                    read = inpStream.read(buffer)
                    if (read == -1) {
                        break
                    }
                    outStream.write(buffer, 0, read)
                }
                outStream.flush()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}