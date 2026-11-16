package com.zuanhinh.lumino.cam.camera

enum class LensFacing { BACK, FRONT }

data class CameraState(
    val lensFacing: LensFacing = LensFacing.BACK,
    val zoomLevel: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 10f,
    val isCapturing: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF
)

enum class FlashMode { OFF, ON, AUTO }
