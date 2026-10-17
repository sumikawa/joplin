package net.cozic.joplin.dex

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.Display
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ViewManager
import net.cozic.joplin.BuildConfig

class DexModePackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(DexModeModule(reactContext))

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}

class DexModeModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context), LifecycleEventListener {
    init {
        context.addLifecycleEventListener(this)
    }

    override fun getName() = "DexModeModule"

    @ReactMethod
    fun getWindowState(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            val activity = getCurrentActivity()
            if (activity == null) Log.i("JoplinDex", "getWindowState: no current Activity")
            promise.resolve(activity?.let { DexModeDetector.windowState(it, it.resources.configuration) } ?: Arguments.createMap().apply {
                putBoolean("isDexMode", false)
                putInt("windowWidth", 0)
            })
        }
    }

    override fun onHostResume() {
        val activity = getCurrentActivity() ?: return
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("dexModeChanged", DexModeDetector.windowState(activity, activity.resources.configuration))
    }

    override fun onHostPause() {}
    override fun onHostDestroy() {}

    override fun invalidate() {
        reactApplicationContext.removeLifecycleEventListener(this)
        super.invalidate()
    }
}

object DexModeDetector {
    private var textLayoutRevision = 0

    fun advanceTextLayoutRevision() {
        textLayoutRevision++
    }

    fun windowState(activity: Activity, configuration: Configuration): WritableMap = Arguments.createMap().apply {
        putBoolean("isDexMode", isDexWindow(activity, configuration))
        // Activity resources follow its display; React Native's global Dimensions may still
        // describe the phone display or the previous size after an external-display resize.
        putInt("windowWidth", configuration.screenWidthDp)
        putInt("densityDpi", activity.resources.displayMetrics.densityDpi)
        putDouble("fontScale", configuration.fontScale.toDouble())
        putInt("textLayoutRevision", textLayoutRevision)
    }

    fun isDexWindow(activity: Activity, configuration: Configuration): Boolean {
        // Emulate a Samsung external app window only in the separate emulator test APK.
        // It still runs through the same detection decision as a physical external window.
        val emulatedExternalWindow = BuildConfig.IS_DEX_EMULATOR_TEST &&
            (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") &&
            activity.intent.getBooleanExtra("joplin.dex.testExternalDisplay", false)

        val samsungDesktopModeEnabled = try {
            val configurationClass = configuration.javaClass
            val enabledValue = configurationClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(null)
            configurationClass.getField("semDesktopModeEnabled").getInt(configuration) == enabledValue
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        val desktopUiMode = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
        // New desktop implementations may keep UI_MODE_TYPE_NORMAL and omit the Samsung field.
        // A mirrored phone screen remains on DEFAULT_DISPLAY; an external app window does not.
        val externalWindow = emulatedExternalWindow || (display != null && display.displayId != Display.DEFAULT_DISPLAY)
        val samsungDevice = emulatedExternalWindow || Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val samsungDesktopWindow = samsungDevice && (desktopUiMode || externalWindow)
        val isDexWindow = samsungDesktopModeEnabled || samsungDesktopWindow
        Log.i("JoplinDex", "display=${display?.displayId} flags=${display?.flags} widthDp=${configuration.screenWidthDp} uiMode=${configuration.uiMode} samsungMode=$samsungDesktopModeEnabled deskUiMode=$desktopUiMode externalWindow=$externalWindow emulatedExternalWindow=$emulatedExternalWindow isDexWindow=$isDexWindow")
        return isDexWindow
    }
}
