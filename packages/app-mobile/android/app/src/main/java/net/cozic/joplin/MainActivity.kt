package net.cozic.joplin
import expo.modules.ReactActivityDelegateWrapper

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.view.ViewTreeObserver
import com.facebook.react.ReactApplication
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.DisplayMetricsHolder
import android.util.Log
import net.cozic.joplin.dex.DexModeDetector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ReactActivity() {

  private val displayMetricsPreDrawListener = ViewTreeObserver.OnPreDrawListener {
    val expected = resources.displayMetrics
    val cached = DisplayMetricsHolder.getScreenDisplayMetrics()
    if (cached.densityDpi != expected.densityDpi || cached.scaledDensity != expected.scaledDensity) {
      // ReactRootView's rotation listener also resets metrics from the phone's
      // application context during global layout, after configuration callbacks.
      // Repair after global layout and rebuild chrome before drawing the frame.
      Log.i("JoplinDex", "Repairing late display metrics: ${cached.densityDpi} -> ${expected.densityDpi}")
      syncDisplayMetrics()
      DexModeDetector.advanceTextLayoutRevision()
      emitDexMode(resources.configuration)
      window.decorView.requestLayout()
      window.decorView.postInvalidateOnAnimation()
      false
    } else {
      true
    }
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "main"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      ReactActivityDelegateWrapper(this, BuildConfig.IS_NEW_ARCHITECTURE_ENABLED,
          object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
              override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                  syncDisplayMetrics()
                  super.onConfigurationChanged(newConfig)
                  // Expo dispatches this delegate asynchronously, after the Activity's
                  // callback returns. RN refreshes PixelUtil from its application context
                  // here, so restore Activity metrics after that refresh as well.
                  syncDisplayMetrics()
                  emitDexMode(newConfig)
              }
          })

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    // Android has already updated this Activity's Resources. Update PixelUtil
    // before React Native measures the new surface, not only after dispatch.
    syncDisplayMetrics()
    super.onConfigurationChanged(newConfig)
    syncDisplayMetrics()
  }

  override fun onResume() {
    // Update before React Native's resume listeners measure text or emit dimensions.
    syncDisplayMetrics()
    super.onResume()
  }

  private fun syncDisplayMetrics() {
    // RN's process-wide PixelUtil metrics can still describe the phone display.
    // Fabric uses the Activity display, so stale metrics clip text and font icons
    // in DeX. Use the Activity context on both external and phone displays.
    // https://github.com/react/react-native/issues/57183
    DisplayMetricsHolder.initDisplayMetrics(this)
    val metrics = DisplayMetricsHolder.getScreenDisplayMetrics()
    Log.i("JoplinDex", "display metrics: densityDpi=${metrics.densityDpi} scaledDensity=${metrics.scaledDensity}")
  }

  private fun emitDexMode(newConfig: android.content.res.Configuration) {
    val reactContext = (application as? ReactApplication)?.reactHost?.currentReactContext
    val windowState = DexModeDetector.windowState(this, newConfig)
    reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      ?.emit("dexModeChanged", windowState)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    emitDexMode(resources.configuration)
  }

  /**
   * This is a workaround to fix the upstream issue https://github.com/facebook/react-native/issues/49759#issuecomment-2918934967
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    syncDisplayMetrics()
    window.decorView.viewTreeObserver.addOnPreDrawListener(displayMetricsPreDrawListener)
    
    if (Build.VERSION.SDK_INT >= 35) {
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
          val innerPadding = insets.getInsets(WindowInsetsCompat.Type.ime())
          rootView.setPadding(
            innerPadding.left,
            innerPadding.top,
            innerPadding.right,
            innerPadding.bottom
          )
          insets
      }
    }
  }

  override fun onDestroy() {
    window.decorView.viewTreeObserver.removeOnPreDrawListener(displayMetricsPreDrawListener)
    super.onDestroy()
  }
}
