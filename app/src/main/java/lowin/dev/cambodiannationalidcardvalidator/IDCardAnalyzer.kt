package lowin.dev.cambodiannationalidcardvalidator

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class IDCardAnalyzer(private val onResult: (Boolean) -> Unit) : ImageAnalysis.Analyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastValidDetectionTime = 0L
    private val DETECTION_COOLDOWN = 1000L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            textRecognizer.process(image)
                .addOnSuccessListener { text ->
                    val detectedText = text.text
                    Log.d("IDCardAnalyzer", "Detected text: $detectedText")

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastValidDetectionTime >= DETECTION_COOLDOWN) {
                        val isValid = validateCambodianID(detectedText)
                        if (isValid) {
                            lastValidDetectionTime = currentTime
                        }
                        onResult(isValid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("IDCardAnalyzer", "Text recognition failed", e)
                    onResult(false)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun validateCambodianID(text: String): Boolean {
        val cleanText = text.replace("\\s+".toRegex(), " ").trim()

        // Front side patterns
        val idNumberPattern = Regex("\\d{9}")
        val mrzPattern = Regex("IDKHM\\d{9}")
        val namePattern = Regex("EN\\s*SREYPHAL", RegexOption.IGNORE_CASE)
        val khmPattern = Regex("KHM", RegexOption.IGNORE_CASE)

        // Back side patterns
        val kingdomPattern = Regex("KINGDOM\\s*OF\\s*CAMBODIA", RegexOption.IGNORE_CASE)
        val identityPattern = Regex("(KHMER\\s*IDENTITY\\s*CARD|IDENTITY\\s*CARD)", RegexOption.IGNORE_CASE)
        val nationPattern = Regex("NATION.*RELIGION.*KING", RegexOption.IGNORE_CASE)

        var score = 0
        val matchedPatterns = mutableListOf<String>()

        // Check front side patterns
        if (idNumberPattern.containsMatchIn(cleanText)) {
            score += 2
            matchedPatterns.add("ID Number")
        }

        if (mrzPattern.containsMatchIn(cleanText)) {
            score += 3
            matchedPatterns.add("MRZ")
        }

        if (namePattern.containsMatchIn(cleanText)) {
            score += 2
            matchedPatterns.add("Name")
        }

        if (khmPattern.containsMatchIn(cleanText)) {
            score += 1
            matchedPatterns.add("KHM")
        }

        // Check back side patterns
        if (kingdomPattern.containsMatchIn(cleanText)) {
            score += 2
            matchedPatterns.add("Kingdom")
        }

        if (identityPattern.containsMatchIn(cleanText)) {
            score += 2
            matchedPatterns.add("Identity")
        }

        if (nationPattern.containsMatchIn(cleanText)) {
            score += 2
            matchedPatterns.add("Nation")
        }

        Log.d("IDCardAnalyzer", "Score: $score, Matched Patterns: ${matchedPatterns.joinToString()}")

        return score >= 4
    }
}