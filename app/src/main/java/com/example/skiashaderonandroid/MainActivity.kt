package com.example.skiashaderonandroid

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.skiashaderonandroid.ui.theme.SkiaShaderOnAndroidTheme
import org.jetbrains.skia.*
import org.jetbrains.skiko.GenericSkikoView
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoView
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkiaShaderOnAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Example()
                }
            }
        }
    }
}

@Composable
fun Example() {

    val sksl = """
            uniform float time;
            uniform float offsetX;
            uniform float offsetY;
            
            float f(vec3 p) {
                p.z -= 10. + time;
                float a = p.z * .1 + offsetX / 1000;
                p.xy *= mat2(cos(a), sin(a), -sin(a) + offsetY / 1000, cos(a));
                return .1 - length(cos(p.xy) + sin(p.yz));
            }
            
            half4 main(vec2 fragcoord) { 
                vec3 d = .5 - fragcoord.xy1 / 500;
                vec3 p=vec3(0);
                for (int i = 0; i < 32; i++) p += f(p) * d;
                return ((sin(p) + vec3(2, 5, 9)) / length(p)).xyz1;
            }
        """

    val runtimeEffect = RuntimeEffect.makeForShader(sksl)
    val byteBuffer = remember {
        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
    }

    var timeUniform by remember { mutableStateOf(0.0f) }
    var previousNanos by remember { mutableStateOf(0L) }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val uniforms = (byteBuffer.clear() as ByteBuffer).apply {
        putFloat(0, timeUniform)
        putFloat(4, offsetX)
        putFloat(8, offsetY)
    }
    val skiaShader: Shader = runtimeEffect.makeShader(
        uniforms = Data.makeFromBytes(uniforms.array()),
        children = null,
        localMatrix = null,
        isOpaque = false
    )
    val paint = remember { Paint() }



    AndroidView(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consumeAllChanges()
                offsetX += dragAmount.x
                offsetY += dragAmount.y
            }
        },
        update = {
            paint.apply { shader = skiaShader }
        },
        factory = { ctx ->
            val frameLayout = FrameLayout(ctx)
            val skiaLayer = SkiaLayer()
            val skikoView = object : SkikoView {
                override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                    canvas.drawRect(
                        Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()),
                        paint
                    )
                }

            }
            skiaLayer.skikoView = GenericSkikoView(skiaLayer, skikoView)
            skiaLayer.attachTo(frameLayout)
            frameLayout
        })


    LaunchedEffect(null) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val nanosPassed = frameTimeNanos - previousNanos
                val delta = nanosPassed / 100000000f
                if (previousNanos > 0.0f) {
                    timeUniform -= delta
                }
                previousNanos = frameTimeNanos
            }
        }
    }

}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SkiaShaderOnAndroidTheme {
        Example()
    }
}