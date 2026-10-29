package com.waterbuddy.app

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 3D மனித கேரக்டர்கள் (Mixamo rig, animation-கள் tools/models/retarget2.py மூலம் சேர்த்தது). */
enum class Model3D(val key: String, val file: String, val displayName: String, val tagline: String, val baseYaw: Float) {
    ARJUN("arjun", "models/arjun.glb", "அர்ஜுன்", "Smart & sporty 🏃", 180f),
    MEERA("meera", "models/meera.glb", "மீரா", "Samba queen 💃", 0f);

    companion object {
        fun fromKey(k: String?): Model3D? = entries.firstOrNull { it.key == k }
    }
}

/** glb-ல இருக்குற animation பெயர்கள் */
enum class Action3D(val anim: String) { IDLE("Idle"), RUN("Run"), DANCE("Dance"), YES("Yes"), NO("No"), SAD("Sad") }

/**
 * Filament மூலம் 3D கேரக்டரை transparent TextureView-ல வரையும்.
 * Animation மாறும்போது மென்மையா cross-fade ஆகும்.
 * முகம் (photo) இருந்தா, 3D தலையை மறைச்சு, head bone இருக்குற இடத்தை onHead மூலம் சொல்லும்.
 */
class Buddy3DController(private val context: Context, private val model: Model3D) :
    UiHelper.RendererCallback, Choreographer.FrameCallback {

    companion object {
        @Volatile private var nativeReady = false
        fun ensureNative() {
            if (!nativeReady) { Gltfio.init(); nativeReady = true }
        }
        private const val FADE_SEC = 0.35f
    }

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val camera: Camera
    private val view: View
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
    private var swapChain: SwapChain? = null
    private val materialProvider: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private val asset: FilamentAsset
    private val animator: Animator
    private val lights = IntArray(2)
    private val indirect: IndirectLight

    private val animIndex = HashMap<String, Int>()
    private var cur = 0
    private var curStart = 0L
    private var prev = -1
    private var prevStart = 0L
    private var fadeStart = 0L
    private var lastFrame = 0L
    private var running = false
    var paused = false
        set(v) { field = v; if (!v) post() }

    var targetYaw = 0f
    private var yaw = 0f
    var hideHead = false
    var onHead: ((x: Float, y: Float, size: Float) -> Unit)? = null

    private var width = 1
    private var height = 1
    private var unitScale = 0f
    private val headE: Int
    private val headTopE: Int
    private val m = FloatArray(16)
    private val viewM = DoubleArray(16)
    private val projM = DoubleArray(16)

    init {
        ensureNative()
        engine = Engine.create()
        renderer = engine.createRenderer()
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        scene = engine.createScene()
        camera = engine.createCamera(EntityManager.get().create()).apply { setExposure(16f, 1f / 125f, 100f) }
        view = engine.createView().also {
            it.scene = scene
            it.camera = camera
            it.blendMode = View.BlendMode.TRANSLUCENT
        }

        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine, true)

        val bytes = context.assets.open(model.file).use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes); buf.flip()
        asset = assetLoader.createAsset(buf) ?: error("3D model load ஆகல")
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        animator = asset.instance.animator
        scene.addEntities(asset.entities)
        for (i in 0 until animator.animationCount) animIndex[animator.getAnimationName(i)] = i
        headE = asset.getFirstEntityByName("mixamorig:Head")
        headTopE = asset.getFirstEntityByName("mixamorig:HeadTop_End")

        // வெளிச்சம்: முன்னாடி மேல இருந்து சூரியன் + பக்கவாட்டு fill + சுற்றுப்புற ஒளி
        lights[0] = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.97f, 0.92f).intensity(110_000f)
            .direction(-0.25f, -0.7f, -0.65f).castShadows(false)
            .build(engine, lights[0])
        lights[1] = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.85f, 0.9f, 1.0f).intensity(35_000f)
            .direction(0.8f, -0.2f, -0.5f).castShadows(false)
            .build(engine, lights[1])
        scene.addEntities(lights)
        indirect = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(1.0f, 1.0f, 1.05f))
            .intensity(28_000f)
            .build(engine)
        scene.indirectLight = indirect

        yaw = model.baseYaw
        setAction(Action3D.IDLE, immediate = true)
    }

    fun attach(tv: TextureView) {
        tv.isOpaque = false
        uiHelper.renderCallback = this
        uiHelper.attachTo(tv)
        running = true
        post()
    }

    fun setAction(a: Action3D, immediate: Boolean = false) {
        val idx = animIndex[a.anim] ?: return
        if (idx == cur && !immediate) return
        val now = System.nanoTime()
        if (immediate) {
            prev = -1
        } else {
            prev = cur; prevStart = curStart; fadeStart = now
        }
        cur = idx; curStart = now
    }

    private fun post() {
        if (running && !paused) {
            Choreographer.getInstance().removeFrameCallback(this)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || paused) return
        Choreographer.getInstance().postFrameCallback(this)
        if (frameTimeNanos - lastFrame < 30_000_000L) return     // ~30fps போதும் (பேட்டரி)
        val dt = if (lastFrame == 0L) 0f else (frameTimeNanos - lastFrame) / 1e9f
        lastFrame = frameTimeNanos
        val sc = swapChain ?: return
        if (!uiHelper.isReadyToRender) return

        val tm = engine.transformManager
        val now = System.nanoTime()

        // முதல் frame: கேரக்டர் உயரம் அளந்து 2 unit-க்கு fit பண்ணு
        if (unitScale == 0f) {
            tm.setTransform(tm.getInstance(asset.root), identity())
            animator.applyAnimation(cur, 0f)
            animator.updateBoneMatrices()
            val h = worldY(if (headTopE != 0) headTopE else headE)
            unitScale = if (h > 0.01f) 2.0f / (h * 1.04f) else 1f
        }

        yaw += (targetYaw + model.baseYaw - yaw) * min(1f, dt * 7f)
        tm.setTransform(tm.getInstance(asset.root), rootMatrix(yaw, unitScale))

        fun animTime(idx: Int, start: Long): Float {
            val d = max(0.01f, animator.getAnimationDuration(idx))
            return ((now - start) / 1e9f) % d
        }
        animator.applyAnimation(cur, animTime(cur, curStart))
        if (prev >= 0) {
            val a = (now - fadeStart) / 1e9f / FADE_SEC
            if (a >= 1f) prev = -1 else animator.applyCrossFade(prev, animTime(prev, prevStart), a)
        }
        if (headE != 0) setLocalScale(headE, if (hideHead) 0.001f else 1f)
        animator.updateBoneMatrices()

        onHead?.let { cb ->
            if (headE != 0 && headTopE != 0) {
                val (hx, hy) = project(headE)
                val (tx, ty) = project(headTopE)
                val cx = hx + (tx - hx) * 0.55f
                val cy = hy + (ty - hy) * 0.55f
                val len = sqrt((tx - hx) * (tx - hx) + (ty - hy) * (ty - hy))
                cb(cx, cy, len * 2.1f)
            }
        }

        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** Translate(0,-1,0) * RotY(yaw) * Scale(s) — column-major */
    private fun rootMatrix(yawDeg: Float, s: Float): FloatArray {
        val r = Math.toRadians(yawDeg.toDouble())
        val c = cos(r).toFloat() * s
        val n = sin(r).toFloat() * s
        return floatArrayOf(c, 0f, -n, 0f, 0f, s, 0f, 0f, n, 0f, c, 0f, 0f, -1f, 0f, 1f)
    }

    private fun worldY(e: Int): Float {
        val tm = engine.transformManager
        tm.getWorldTransform(tm.getInstance(e), m)
        return m[13]
    }

    /** Bone-ஓட local scale-ஐ absolute-ஆ செட் பண்ணும் (rotation அப்படியே) */
    private fun setLocalScale(e: Int, s: Float) {
        val tm = engine.transformManager
        val inst = tm.getInstance(e)
        tm.getTransform(inst, m)
        for (col in 0 until 3) {
            val b = col * 4
            val len = sqrt(m[b] * m[b] + m[b + 1] * m[b + 1] + m[b + 2] * m[b + 2])
            if (len > 1e-6f) {
                val k = s / len
                m[b] *= k; m[b + 1] *= k; m[b + 2] *= k
            }
        }
        tm.setTransform(inst, m)
    }

    private fun project(e: Int): Pair<Float, Float> {
        val tm = engine.transformManager
        tm.getWorldTransform(tm.getInstance(e), m)
        val x = m[12].toDouble(); val y = m[13].toDouble(); val z = m[14].toDouble()
        camera.getViewMatrix(viewM)
        camera.getProjectionMatrix(projM)
        val vx = viewM[0] * x + viewM[4] * y + viewM[8] * z + viewM[12]
        val vy = viewM[1] * x + viewM[5] * y + viewM[9] * z + viewM[13]
        val vz = viewM[2] * x + viewM[6] * y + viewM[10] * z + viewM[14]
        val vw = viewM[3] * x + viewM[7] * y + viewM[11] * z + viewM[15]
        val cx = projM[0] * vx + projM[4] * vy + projM[8] * vz + projM[12] * vw
        val cy = projM[1] * vx + projM[5] * vy + projM[9] * vz + projM[13] * vw
        val cw = projM[3] * vx + projM[7] * vy + projM[11] * vz + projM[15] * vw
        if (cw == 0.0) return 0f to 0f
        val nx = cx / cw; val ny = cy / cw
        return ((nx * 0.5 + 0.5) * width).toFloat() to ((1.0 - (ny * 0.5 + 0.5)) * height).toFloat()
    }

    override fun onNativeWindowChanged(surface: Surface) {
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
    }

    override fun onDetachedFromSurface() {
        swapChain?.let {
            engine.destroySwapChain(it)
            engine.flushAndWait()
            swapChain = null
        }
    }

    override fun onResized(w: Int, h: Int) {
        width = max(1, w); height = max(1, h)
        view.viewport = Viewport(0, 0, width, height)
        camera.setLensProjection(42.0, width.toDouble() / height, 0.1, 100.0)
        camera.lookAt(0.0, 0.05, 5.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    fun release() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        try {
            uiHelper.detach()
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = null
            scene.removeEntities(asset.entities)
            assetLoader.destroyAsset(asset)
            materialProvider.destroyMaterials()
            materialProvider.destroy()
            resourceLoader.destroy()
            assetLoader.destroy()
            engine.destroyIndirectLight(indirect)
            lights.forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroyCameraComponent(camera.entity)
            EntityManager.get().destroy(camera.entity)
            engine.destroy()
        } catch (_: Throwable) {
        }
    }
}

/**
 * 3D கேரக்டர் composable. [face] இருந்தா அந்த cartoon முகம் தலை மேல ஒட்டிக்கும் (bobblehead ஸ்டைல்).
 * 3D load ஆகலைன்னா [fallback] காட்டும்.
 */
@Composable
fun Buddy3D(
    model: Model3D,
    action: Action3D,
    modifier: Modifier = Modifier,
    yaw: Float = 0f,
    face: ImageBitmap? = null,
    fallback: @Composable () -> Unit = {}
) {
    val ctx = LocalContext.current
    val controller = remember(model) {
        try {
            Buddy3DController(ctx.applicationContext, model)
        } catch (t: Throwable) {
            DebugLog.add(ctx, "3D load பிழை: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }
    if (controller == null) {
        Box(modifier, contentAlignment = Alignment.Center) { fallback() }
        return
    }
    val hx = remember { mutableFloatStateOf(0f) }
    val hy = remember { mutableFloatStateOf(0f) }
    val hs = remember { mutableFloatStateOf(0f) }

    DisposableEffect(controller) { onDispose { controller.release() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> controller.paused = true
                Lifecycle.Event.ON_RESUME -> controller.paused = false
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(action) { controller.setAction(action) }
    SideEffect {
        controller.targetYaw = yaw
        controller.hideHead = face != null
        controller.onHead = if (face != null) { x, y, s -> hx.floatValue = x; hy.floatValue = y; hs.floatValue = s } else null
    }

    val basePx = with(LocalDensity.current) { 100.dp.toPx() }
    Box(modifier) {
        AndroidView(
            factory = { c -> TextureView(c).also { controller.attach(it) } },
            modifier = Modifier.fillMaxSize()
        )
        if (face != null) {
            Image(
                bitmap = face,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val s = hs.floatValue / basePx
                        scaleX = s; scaleY = s
                        translationX = hx.floatValue - hs.floatValue / 2f
                        translationY = hy.floatValue - hs.floatValue / 2f
                        alpha = if (hs.floatValue > 1f) 1f else 0f
                    }
            )
        }
    }
}
