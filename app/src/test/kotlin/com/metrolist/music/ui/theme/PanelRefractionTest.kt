package com.metrolist.music.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelRefractionTest {
    @Test
    fun `pane refracts pixels deep inside the panel while keeping its optical centre fixed`() {
        val source = Bitmap.createBitmap(200, 160, Bitmap.Config.ARGB_8888)
        source.setPixels(IntArray(200 * 160) { Color.rgb(it % 200, it / 200, 40) }, 0, 200, 0, 0, 200, 160)
        fun render(code: String): Bitmap {
            // Robolectric's software Skia uses the older spelling for child-shader sampling.
            val shader = RuntimeShader(code.replace("content.eval(", "sample(content, "))
            if (code.contains("uniform float2 size")) shader.setFloatUniform("size", 200f, 160f)
            shader.setInputShader("content", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            val result = Bitmap.createBitmap(200, 160, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            // Enable runtime effects in the native Skia test canvas.
            ReflectionHelpers.callInstanceMethod<Void>(canvas, "setHwFeaturesInSwModeEnabled",
                ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            canvas.drawRect(0f, 0f, 200f, 160f, Paint().apply { this.shader = shader })
            return result
        }
        val baseline = render("uniform shader content; half4 main(float2 p) { return content.eval(p); }")
        val output = render(SounPanelLensShader)
        val refractedX = Color.red(output.getPixel(50, 80))
        val originalX = Color.red(baseline.getPixel(50, 80))
        assertTrue("Interior x must move toward centre: $refractedX versus $originalX", refractedX > originalX)
        assertTrue("Interior y must move toward centre", Color.green(output.getPixel(100, 40)) > Color.green(baseline.getPixel(100, 40)))
        assertTrue("The optical centre must remain centred", kotlin.math.abs(Color.red(output.getPixel(100, 80)) - Color.red(baseline.getPixel(100, 80))) <= 2)

    }
}
