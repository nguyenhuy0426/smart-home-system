package com.android.smarthome.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the bundled end2end YOLO26n person/fall model against camera frames.
 *
 * The model contract is validated when the session is created: NCHW RGB float input
 * [1, 3, 640, 640] and output [1, maxDetections, 6] where each row is
 * x1, y1, x2, y2, confidence, classId with NMS already embedded in the graph
 * (Ultralytics end2end export). Classes: 0=person_fallen, 1=person.
 */
class OnnxInferenceService(private val context: Context) {

    companion object {
        private const val TAG = "OnnxInferenceService"
        private const val MODEL_ASSET = "smarthome_person_fall_yolo26n_end2end_640_cls0_fallen.onnx"
        private const val INPUT_SIZE = 640
        private const val OUTPUT_VALUES_PER_DETECTION = 6L
        // The training notebook evaluates at 0.25. TODO_USER_CONFIG: if the training run produced
        // a deploy_config.txt with a tuned RECOMMENDED_CONF_FALLEN (recall-targeted sweep), copy
        // that value here before relying on fall alerts.
        private const val FALLEN_CONF_THRESHOLD = 0.25f
        private const val PERSON_CONF_THRESHOLD = 0.25f
        private val INPUT_SHAPE = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        // Indexed by class id: 0=person_fallen, 1=person.
        private val CLASS_CONF_THRESHOLDS =
            floatArrayOf(FALLEN_CONF_THRESHOLD, PERSON_CONF_THRESHOLD)
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val inferenceInFlight = AtomicBoolean(false)

    @Volatile
    private var isInitialized = false

    /** Non-null when [init] failed; lets callers surface why detection is inactive. */
    @Volatile
    var initializationError: String? = null
        private set

    data class Detection(
        val classIndex: Int,
        val className: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    interface DetectionListener {
        fun onDetectionsResult(detections: List<Detection>, width: Int, height: Int)
    }

    @Synchronized
    fun init() {
        if (isInitialized) return

        var options: OrtSession.SessionOptions? = null
        try {
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env
            options = OrtSession.SessionOptions().apply {
                // The Raspberry Pi gateway is CPU-bound. Avoid ORT creating an unbounded pool for
                // each session while the application already serializes frame inference.
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }

            session = createValidatedSession(env, options, MODEL_ASSET)
            isInitialized = true
            initializationError = null
            Log.i(TAG, "Person/fall end2end ONNX session initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX session", e)
            initializationError = e.message ?: e.javaClass.simpleName
            session?.close()
            session = null
            isInitialized = false
        } finally {
            options?.close()
        }
    }

    private fun createValidatedSession(
        env: OrtEnvironment,
        options: OrtSession.SessionOptions,
        assetName: String
    ): OrtSession {
        Log.i(TAG, "Loading ONNX model: $assetName")
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        val session = env.createSession(bytes, options)
        try {
            val inputName = session.inputNames.singleOrNull()
                ?: throw IllegalStateException("$assetName must expose exactly one input")
            val inputShape = (session.inputInfo[inputName]?.info as? ai.onnxruntime.TensorInfo)?.shape
                ?: throw IllegalStateException("$assetName input must be a tensor")
            if (!inputShape.contentEquals(INPUT_SHAPE)) {
                throw IllegalStateException(
                    "$assetName input shape ${inputShape.contentToString()} does not match " +
                        INPUT_SHAPE.contentToString()
                )
            }

            val outputName = session.outputNames.singleOrNull()
                ?: throw IllegalStateException("$assetName must expose exactly one output")
            val outputShape = (session.outputInfo[outputName]?.info as? ai.onnxruntime.TensorInfo)?.shape
                ?: throw IllegalStateException("$assetName output must be a tensor")
            if (outputShape.size != 3 || outputShape[0] != 1L ||
                outputShape[1] <= 0L || outputShape[2] != OUTPUT_VALUES_PER_DETECTION) {
                throw IllegalStateException(
                    "$assetName output shape ${outputShape.contentToString()} is not an " +
                        "end2end YOLO [1, detections, 6] tensor"
                )
            }

            val names = session.metadata.customMetadata["names"]
            if (names != null &&
                !names.contains(YoloOutputDecoder.CLASS_NAME_FALLEN, ignoreCase = true)) {
                throw IllegalStateException(
                    "$assetName metadata names '$names' does not contain " +
                        "'${YoloOutputDecoder.CLASS_NAME_FALLEN}'"
                )
            }
            return session
        } catch (e: Exception) {
            session.close()
            throw e
        }
    }

    /**
     * Schedules inference without allowing camera frames to form an unbounded backlog. A new frame
     * is dropped while the previous one is being processed, which keeps the displayed result live.
     */
    fun runInference(bitmap: Bitmap, listener: DetectionListener) {
        if (!isInitialized) {
            Log.w(TAG, "ONNX session is not initialized: ${initializationError ?: "init() not called"}")
            return
        }
        if (!inferenceInFlight.compareAndSet(false, true)) {
            Log.v(TAG, "Dropping camera frame while inference is busy")
            return
        }

        try {
            inferenceExecutor.execute {
                try {
                    runInferenceInternal(bitmap, listener)
                } catch (e: Exception) {
                    Log.e(TAG, "ONNX inference error", e)
                } finally {
                    inferenceInFlight.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            inferenceInFlight.set(false)
        }
    }

    private fun runInferenceInternal(bitmap: Bitmap, listener: DetectionListener) {
        val env = ortEnv ?: return
        val activeSession = session ?: return
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        if (originalWidth <= 0 || originalHeight <= 0) return

        val (inputBuffer, params) = preprocessLetterbox(bitmap)
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, INPUT_SHAPE)
        try {
            val detections =
                runModel(activeSession, inputTensor, params, originalWidth, originalHeight)
            listener.onDetectionsResult(detections, originalWidth, originalHeight)
        } finally {
            inputTensor.close()
        }
    }

    private fun runModel(
        session: OrtSession,
        inputTensor: OnnxTensor,
        params: LetterboxParams,
        originalWidth: Int,
        originalHeight: Int
    ): List<Detection> {
        val inputName = session.inputNames.single()
        val results = session.run(mapOf(inputName to inputTensor))
        try {
            if (results.size() != 1) {
                throw IllegalStateException("Model returned ${results.size()} outputs")
            }
            val output = results.get(0) as? OnnxTensor
                ?: throw IllegalStateException("Model output is not a tensor")
            val shape = output.info.shape
            if (shape.size != 3 || shape[0] != 1L || shape[2] != OUTPUT_VALUES_PER_DETECTION) {
                throw IllegalStateException(
                    "Model returned unsupported shape ${shape.contentToString()}"
                )
            }
            return YoloOutputDecoder.decode(
                output.floatBuffer,
                shape,
                CLASS_CONF_THRESHOLDS,
                params.scale,
                params.padLeft,
                params.padTop,
                originalWidth,
                originalHeight
            ).map { decoded ->
                Detection(
                    decoded.classId,
                    decoded.className,
                    decoded.confidence,
                    RectF(decoded.left, decoded.top, decoded.right, decoded.bottom)
                )
            }
        } finally {
            results.close()
        }
    }

    private data class LetterboxParams(val scale: Float, val padLeft: Int, val padTop: Int)

    private fun preprocessLetterbox(bitmap: Bitmap): Pair<FloatBuffer, LetterboxParams> {
        val scale = minOf(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )
        // Match the export/calibration pipeline, which rounds the resized dimensions.
        val resizedWidth = maxOf(1, kotlin.math.round(bitmap.width * scale).toInt())
        val resizedHeight = maxOf(1, kotlin.math.round(bitmap.height * scale).toInt())
        val resized = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
        val letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val padLeft = (INPUT_SIZE - resizedWidth) / 2
        val padTop = (INPUT_SIZE - resizedHeight) / 2

        try {
            Canvas(letterboxed).apply {
                drawColor(Color.rgb(114, 114, 114))
                drawBitmap(resized, padLeft.toFloat(), padTop.toFloat(), Paint())
            }

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            letterboxed.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            val planeSize = INPUT_SIZE * INPUT_SIZE
            val buffer = FloatBuffer.allocate(3 * planeSize)
            for (index in pixels.indices) {
                buffer.put(index, ((pixels[index] ushr 16) and 0xff) / 255f)
                buffer.put(planeSize + index, ((pixels[index] ushr 8) and 0xff) / 255f)
                buffer.put(2 * planeSize + index, (pixels[index] and 0xff) / 255f)
            }
            buffer.rewind()
            return Pair(buffer, LetterboxParams(scale, padLeft, padTop))
        } finally {
            if (resized !== bitmap) resized.recycle()
            letterboxed.recycle()
        }
    }

    @Synchronized
    fun close() {
        isInitialized = false
        inferenceExecutor.shutdown()
        try {
            if (!inferenceExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                inferenceExecutor.shutdownNow()
                inferenceExecutor.awaitTermination(2, TimeUnit.SECONDS)
            }
        } catch (_: InterruptedException) {
            inferenceExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        session?.close()
        session = null
        ortEnv?.close()
        ortEnv = null
    }
}
