package com.zuanhinh.lumino.cam.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Camera2Controller(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private val backgroundThread = HandlerThread("CameraBackground").also { it.start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var currentLensFacing = LensFacing.BACK
    private var currentFlashMode = FlashMode.OFF
    private var currentZoom = 1f
    private var previewSurface: Surface? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun getCameraId(lensFacing: LensFacing): String? {
        val facing = if (lensFacing == LensFacing.BACK)
            CameraCharacteristics.LENS_FACING_BACK
        else
            CameraCharacteristics.LENS_FACING_FRONT
        return cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(lensFacing: LensFacing, surface: Surface, onOpened: () -> Unit) {
        currentLensFacing = lensFacing
        previewSurface = surface
        val cameraId = getCameraId(lensFacing) ?: return

        imageReader = ImageReader.newInstance(3840, 2160, ImageFormat.JPEG, 2)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreview(surface)
                onOpened()
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) {
                Log.e("Camera2", "Error opening camera: ${'$'}error")
                camera.close()
            }
        }, backgroundHandler)
    }

    private fun startPreview(surface: Surface) {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return

        previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        device.createCaptureSession(
            listOf(surface, reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(
                        previewRequestBuilder!!.build(), null, backgroundHandler
                    )
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera2", "Session configure failed")
                }
            }, backgroundHandler
        )
    }

    fun tapToFocus(x: Float, y: Float) {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        session.capture(builder.build(), null, backgroundHandler)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        session.capture(builder.build(), null, backgroundHandler)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
    }

    fun setZoom(zoom: Float) {
        currentZoom = zoom
        val cameraId = getCameraId(currentLensFacing) ?: return
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val clamped = zoom.coerceIn(1f, maxZoom)
        val sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val cropW = (sensorRect.width() / clamped).toInt()
        val cropH = (sensorRect.height() / clamped).toInt()
        val cropX = (sensorRect.width() - cropW) / 2
        val cropY = (sensorRect.height() - cropH) / 2
        val cropRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
        previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
        captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)
    }

    fun capturePhoto(onSaved: (String) -> Unit) {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val surface = previewSurface ?: return

        val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.JPEG_QUALITY, 95)
            when (currentFlashMode) {
                FlashMode.ON -> set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                FlashMode.AUTO -> set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                FlashMode.OFF -> set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
        }

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()
            saveToGallery(bytes, onSaved)
        }, backgroundHandler)

        captureSession?.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                startPreview(surface)
            }
        }, backgroundHandler)
    }

    private fun saveToGallery(bytes: ByteArray, onSaved: (String) -> Unit) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "LUMINO_${'$'}timestamp.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Lumino Camera")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val stream: OutputStream? = context.contentResolver.openOutputStream(it)
            stream?.write(bytes)
            stream?.close()
            onSaved(filename)
        }
    }

    fun close() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        backgroundThread.quitSafely()
    }
}
