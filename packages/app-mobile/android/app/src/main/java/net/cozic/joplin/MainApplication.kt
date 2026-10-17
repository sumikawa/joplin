package net.cozic.joplin
import android.content.res.Configuration
import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ExpoReactHostFactory

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactHost
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlags
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsProvider
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsOverrides_RNOSS_Stable_Android
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsOverrides_RNOSS_Canary_Android
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsOverrides_RNOSS_Experimental_Android
import android.util.Log
import net.cozic.joplin.systeminfo.SystemInformationPackage
import net.cozic.joplin.share.SharePackage
import net.cozic.joplin.ssl.SslPackage
import net.cozic.joplin.dex.DexModePackage

class MainApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        ExpoReactHostFactory.getDefaultReactHost(
            context = applicationContext,
            useDevSupport = BuildConfig.DEBUG,
            packageList = PackageList(this).packages.apply {
                add(SharePackage())
                add(SslPackage())
                add(SystemInformationPackage())
                add(DexModePackage())
            },
        )
    }

    override fun onCreate() {
        super.onCreate()

        try {
            DefaultNewArchitectureEntryPoint.releaseLevel = ReleaseLevel.valueOf(BuildConfig.REACT_NATIVE_RELEASE_LEVEL.uppercase())
        } catch (e: IllegalArgumentException) {
            DefaultNewArchitectureEntryPoint.releaseLevel = ReleaseLevel.STABLE
        }
        loadReactNative(this)
        // Configure before creating the ReactHost/runtime. RN 0.86's text cache
        // does not key by display density and can retain measurements taken
        // before MainActivity repairs metrics during a move to/from DeX.
        // Preserve the selected release channel's defaults for every other flag.
        val defaults = when (DefaultNewArchitectureEntryPoint.releaseLevel) {
            ReleaseLevel.EXPERIMENTAL -> ReactNativeFeatureFlagsOverrides_RNOSS_Experimental_Android()
            ReleaseLevel.CANARY -> ReactNativeFeatureFlagsOverrides_RNOSS_Canary_Android()
            ReleaseLevel.STABLE -> ReactNativeFeatureFlagsOverrides_RNOSS_Stable_Android()
        }
        val accessedFlags = ReactNativeFeatureFlags.dangerouslyForceOverride(
            object : ReactNativeFeatureFlagsProvider by defaults {
                override fun disableTextLayoutManagerCacheAndroid() = true
            }
        )
        Log.i("JoplinDex", "Disabled density-unaware text cache before runtime; previously read flags=$accessedFlags")
        ApplicationLifecycleDispatcher.onApplicationCreate(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
    }
}
