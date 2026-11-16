package com.zuanhinh.lumino.cam.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zuanhinh.lumino.cam.camera.Camera2Controller
import com.zuanhinh.lumino.cam.camera.FlashMode
import com.zuanhinh.lumino.cam.camera.LensFacing

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val controller = remember { Camera2Controller(context) }

    var lensFacing by remember { mutableStateOf(LensFacing.BACK) }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    DisposableEffect(Unit) {
        onDispose { controller.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            currentSurface = holder.surface
                            controller.openCamera(lensFacing, holder.surface) {}
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {}
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset ->
                        controller.tapToFocus(offset.x, offset.y)
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 10f)
                        controller.setZoom(zoom)
                    }
                }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = {
                flashMode = when (flashMode) {
                    FlashMode.OFF -> FlashMode.ON
                    FlashMode.ON -> FlashMode.AUTO
                    FlashMode.AUTO -> FlashMode.OFF
                }
            }) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.OFF -> Icons.Filled.FlashOff
                        FlashMode.ON -> Icons.Filled.FlashOn
                        FlashMode.AUTO -> Icons.Filled.FlashAuto
                    },
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(56.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            controller.capturePhoto { filename: String ->
                                Toast.makeText(context, "Saved: ${'$'}filename", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
            )

            IconButton(onClick = {
                lensFacing = if (lensFacing == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
                currentSurface?.let { surface ->
                    controller.openCamera(lensFacing, surface) {}
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
