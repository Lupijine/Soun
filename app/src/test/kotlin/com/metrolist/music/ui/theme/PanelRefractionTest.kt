package com.metrolist.music.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelRefractionTest {
    private fun render(source: Bitmap, code: String): Bitmap {
        // Robolectric's software Skia uses the older spelling for child-shader sampling.
        // The native test backend leaves vector uniforms at zero; fix the fixture's viewport in the same shader.
        val fixtureCode = code.replace("uniform float2 size;", "const float2 size = float2(${source.width}.0, ${source.height}.0);")
        val shader = RuntimeShader(fixtureCode.replace("content.eval(", "sample(content, "))
        shader.setInputShader("content", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        ReflectionHelpers.callInstanceMethod<Void>(canvas, "setHwFeaturesInSwModeEnabled",
            ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), Paint().apply { this.shader = shader })
        return result
    }

    @Test
    fun `interior bends nonlinearly including the centre rather than uniformly enlarging the backdrop`() {
        val source = Bitmap.createBitmap(200, 160, Bitmap.Config.ARGB_8888)
        source.setPixels(IntArray(200 * 160) { Color.rgb(it % 200, it / 200, 40) }, 0, 200, 0, 0, 200, 160)
        val baseline = render(source, "uniform shader content; half4 main(float2 p) { return content.eval(p); }")
        val output = render(source, SounPanelLensShader)
        fun dx(x: Int) = Color.red(output.getPixel(x, 80)) - Color.red(baseline.getPixel(x, 80))
        val centre = dx(100)
        assertTrue("Even the interior centre must refract", abs(centre) >= 2)
        // Translation, zoom and every affine transform have zero second difference on this coordinate ramp.
        assertTrue("Three interior samples must reject a uniform zoom: ${dx(50)}, $centre, ${dx(150)}",
            abs(dx(50) + dx(150) - 2 * centre) >= 4)
    }

    @Test
    fun `portrait pane samples stay inside the source and the surface bends a text grid`() {
        val source = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        canvas.drawColor(Color.rgb(33, 24, 45))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0..7) for (x in 0..3) {
            paint.color = Color.rgb(55 + x * 48, 40 + y * 22, 100)
            canvas.drawRoundRect(x * 90f + 8, y * 80f + 8, x * 90f + 82, y * 80f + 72, 12f, 12f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            canvas.drawText("${x + 1} · SONG", x * 90f + 10, y * 80f + 48, paint)
        }
        val output = render(source, SounPanelLensShader)
        for ((x, y) in listOf(0 to 0, 359 to 0, 0 to 639, 359 to 639, 180 to 320)) {
            assertTrue("Refraction must not introduce transparent holes", Color.alpha(output.getPixel(x, y)) == 255)
        }
        System.getenv("SOUN_VISUAL_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "glass-grid-before.png").outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "glass-grid-after.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
