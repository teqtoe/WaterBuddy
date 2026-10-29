package com.waterbuddy.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * உங்க photo-ல இருந்து முகத்தை கண்டுபிடிச்சு (ML Kit — phone-லயே, internet தேவையில்லை),
 * cartoon ஸ்டைல்ல மாத்தி, ஒரு sticker மாதிரி சேமிக்கும். அந்த முகம் 3D கேரக்டர் தலையில ஒட்டிக்கும்.
 */
object FaceMaker {
    private const val OUT = 360

    fun file(ctx: Context) = File(ctx.filesDir, "face.png")

    fun load(ctx: Context): ImageBitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() } catch (_: Exception) { null }
    }

    sealed class Result {
        data class Ok(val face: ImageBitmap) : Result()
        data class Fail(val reason: String) : Result()
    }

    suspend fun make(ctx: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        try {
            val src = decode(ctx, uri, 1400) ?: return@withContext Result.Fail("Photo திறக்க முடியல")
            val box = detect(src) ?: return@withContext Result.Fail("Photo-ல முகம் கண்டுபிடிக்க முடியல. முகம் தெளிவா தெரியுற photo போடுங்க.")
            // முடியும் தாடையும் சேர்த்து கொஞ்சம் பெருசா வெட்டு
            val cx = box.exactCenterX()
            val cy = box.exactCenterY() - box.height() * 0.10f
            val w = box.width() * 1.55f
            val h = box.height() * 1.85f
            val crop = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            val ew = (OUT * 0.80f).roundToInt()
            val eh = OUT
            val faceBmp = Bitmap.createBitmap(ew, eh, Bitmap.Config.ARGB_8888)
            Canvas(faceBmp).apply {
                drawColor(Color.WHITE)
                val mtx = Matrix().apply { setRectToRect(crop, RectF(0f, 0f, ew.toFloat(), eh.toFloat()), Matrix.ScaleToFit.FILL) }
                drawBitmap(src, mtx, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            src.recycle()
            toon(faceBmp)
            val out = sticker(faceBmp)
            file(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Result.Ok(out.asImageBitmap())
        } catch (e: Throwable) {
            DebugLog.add(ctx, "FaceMaker பிழை: ${e.javaClass.simpleName}: ${e.message}")
            Result.Fail("ஏதோ தப்பாயிடுச்சு. வேற photo try பண்ணுங்க.")
        }
    }

    fun clear(ctx: Context) { file(ctx).delete() }

    // ───────────────────────── உள் வேலைகள் ─────────────────────────

    private fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val k = min(1f, maxSide.toFloat() / max(s.width, s.height))
                decoder.setTargetSize(max(1, (s.width * k).toInt()), max(1, (s.height * k).toInt()))
            }.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val rot = ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rot == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        }
    }

    private suspend fun detect(bmp: Bitmap): Rect? = suspendCancellableCoroutine { cont ->
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.08f)
                .build()
        )
        detector.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { faces ->
                val best = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                cont.resume(best?.boundingBox)
                detector.close()
            }
            .addOnFailureListener {
                cont.resume(null)
                detector.close()
            }
    }

    /** Cartoon filter: தோல் மென்மை (bilateral) → நிற அடுக்கு (posterize) → கருப்பு கோடுகள் (edges) */
    private fun toon(b: Bitmap) {
        val w = b.width; val h = b.height
        var px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        repeat(2) { px = bilateral(px, w, h, 3, 26f) }

        val gray = FloatArray(w * h) { i ->
            val c = px[i]; 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }
        val levels = 9f
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = px[i]
            var r = Color.red(c).toFloat(); var g = Color.green(c).toFloat(); var bl = Color.blue(c).toFloat()
            // கொஞ்சம் கூடுதல் நிறம்
            val l = gray[i]
            r = l + (r - l) * 1.25f; g = l + (g - l) * 1.25f; bl = l + (bl - l) * 1.25f
            fun q(v: Float) = ((v.coerceIn(0f, 255f) / 255f * (levels - 1)).roundToInt() / (levels - 1) * 255f)
            r = q(r); g = q(g); bl = q(bl)
            // Sobel edge
            if (x in 1 until w - 1 && y in 1 until h - 1) {
                fun G(xx: Int, yy: Int) = gray[yy * w + xx]
                val gx = -G(x - 1, y - 1) - 2 * G(x - 1, y) - G(x - 1, y + 1) + G(x + 1, y - 1) + 2 * G(x + 1, y) + G(x + 1, y + 1)
                val gy = -G(x - 1, y - 1) - 2 * G(x, y - 1) - G(x + 1, y - 1) + G(x - 1, y + 1) + 2 * G(x, y + 1) + G(x + 1, y + 1)
                val mag = sqrt(gx * gx + gy * gy)
                if (mag > 70f) {
                    val k = min(1f, (mag - 70f) / 90f) * 0.8f
                    r = r * (1 - k) + 40f * k; g = g * (1 - k) + 26f * k; bl = bl * (1 - k) + 22f * k
                }
            }
            out[i] = Color.rgb(r.roundToInt().coerceIn(0, 255), g.roundToInt().coerceIn(0, 255), bl.roundToInt().coerceIn(0, 255))
        }
        b.setPixels(out, 0, w, 0, 0, w, h)
    }

    private fun bilateral(src: IntArray, w: Int, h: Int, rad: Int, sigma: Float): IntArray {
        val lut = FloatArray(766) { d -> exp(-(d * d) / (2f * sigma * sigma * 9f)) }
        val dst = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            val c = src[y * w + x]
            val cr = Color.red(c); val cg = Color.green(c); val cb = Color.blue(c)
            var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
            for (dy in -rad..rad) {
                val yy = (y + dy).coerceIn(0, h - 1)
                for (dx in -rad..rad) {
                    val xx = (x + dx).coerceIn(0, w - 1)
                    val n = src[yy * w + xx]
                    val nr = Color.red(n); val ng = Color.green(n); val nb = Color.blue(n)
                    val wgt = lut[abs(nr - cr) + abs(ng - cg) + abs(nb - cb)]
                    sr += nr * wgt; sg += ng * wgt; sb += nb * wgt; sw += wgt
                }
            }
            dst[y * w + x] = Color.rgb((sr / sw).roundToInt(), (sg / sw).roundToInt(), (sb / sw).roundToInt())
        }
        return dst
    }

    /** நீள்வட்ட sticker: ஓரம் மென்மையா மறையும் + வெள்ளை border */
    private fun sticker(face: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val left = (OUT - face.width) / 2f
        val oval = RectF(left, 0f, left + face.width, OUT.toFloat())
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawOval(oval, border)
        val inner = RectF(oval.left + 6f, oval.top + 6f, oval.right - 6f, oval.bottom - 6f)
        val layer = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        Canvas(layer).apply {
            drawBitmap(face, null, inner, Paint(Paint.FILTER_BITMAP_FLAG))
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                shader = RadialGradient(
                    inner.centerX(), inner.centerY(), inner.height() / 2f,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.9f, 1f), Shader.TileMode.CLAMP
                )
            }
            save()
            scale(inner.width() / inner.height(), 1f, inner.centerX(), inner.centerY())
            drawRect(-OUT.toFloat(), -OUT.toFloat(), 2f * OUT, 2f * OUT, mask)   // வட்டத்துக்கு வெளியே எல்லாம் transparent
            restore()
        }
        c.drawBitmap(layer, 0f, 0f, null)
        face.recycle(); layer.recycle()
        return out
    }
}
