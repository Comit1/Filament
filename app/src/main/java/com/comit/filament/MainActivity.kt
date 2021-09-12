package com.comit.filament

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.opengl.Matrix
import android.os.Bundle
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            Utils.init()
        }
    }

    // The View we want to render into
    private lateinit var textureView: TextureView
    private lateinit var modelViewer: MyModelViewer

    private lateinit var choreographer: Choreographer
    private val frameScheduler = FrameCallback()

    private lateinit var doubleTapDetector: GestureDetector
    private val doubleTapListener = DoubleTapListener()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        textureView = TextureView(this)
//        setContentView(textureView)

        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.texture_view)

//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        choreographer = Choreographer.getInstance()

        doubleTapDetector = GestureDetector(applicationContext, doubleTapListener)

        modelViewer = MyModelViewer(textureView)

        textureView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            doubleTapDetector.onTouchEvent(event)
            true
        }

        createDefaultRenderables()

    }

    private fun createDefaultRenderables() {
        val buffer = assets.open("models/scene.gltf").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }

        modelViewer.loadModelGltfAsync(buffer) { uri -> readCompressedAsset("models/$uri") }
        modelViewer.transformToUnitCube()
    }

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    private val animatorOut = ValueAnimator.ofFloat(0.0f, 360.0f)

    private fun startAnimation() {
        // Animate the triangle
        animatorOut.interpolator = LinearInterpolator()
        animatorOut.duration = 6000
        animatorOut.repeatMode = ValueAnimator.RESTART
        animatorOut.repeatCount = ValueAnimator.INFINITE
        animatorOut.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            val transformMatrix = FloatArray(16)
            override fun onAnimationUpdate(a: ValueAnimator) {
                Matrix.setRotateM(transformMatrix, 0, a.animatedValue as Float, 0.0f, 1.0f, 0.0f)
                val tcm = modelViewer.engine.transformManager
                tcm.setTransform(tcm.getInstance(modelViewer.asset!!.root), transformMatrix)
            }
        })
        animatorOut.start()
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameScheduler)
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            modelViewer.animator?.apply {
                if (animationCount > 0) {
                    val elapsedTimeSeconds = (frameTimeNanos - startTime).toDouble() / 1_000_000_000
                    applyAnimation(0, elapsedTimeSeconds.toFloat())
                }
                updateBoneMatrices()
            }

            modelViewer.render(frameTimeNanos)
        }
    }

    // Just for testing purposes, this releases the current model and reloads the default model.
    inner class DoubleTapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent?): Boolean {
            modelViewer.destroyModel()
            createDefaultRenderables()
            return super.onDoubleTap(e)
        }
    }
}