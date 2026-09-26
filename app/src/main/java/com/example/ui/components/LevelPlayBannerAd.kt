package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.AdConfig
import com.example.ads.LevelPlayManager

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Clean Jetpack Compose wrapper for Unity LevelPlay Banner ad.
 * Embeds the singleton LevelPlayBannerAdView safely into non-gameplay screens.
 */
@Composable
fun LevelPlayBannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isInitialized by LevelPlayManager.isInitialized.collectAsStateWithLifecycle()

    if (!isInitialized) {
        // Do not render empty blocking space if ads are uninitialized
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface)
            .testTag("levelplay_banner_container"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    try {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    } catch (_: Throwable) {}
                    try {
                        val act = (ctx as? Activity) ?: ctx.findActivity() ?: (context as? Activity) ?: context.findActivity()
                        if (act != null) {
                            LevelPlayManager.updateCurrentActivity(act)
                        }
                        val banner = LevelPlayManager.getOrCreateBannerView(act ?: ctx)
                        if (banner != null) {
                            (banner.parent as? ViewGroup)?.removeView(banner)
                            try {
                                banner.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            } catch (_: Throwable) {}
                            addView(banner)
                        }
                    } catch (e: Throwable) {
                        Log.w("LevelPlayBannerAd", "Notice embedding banner view: ${e.message}")
                    }
                }
            },
            update = { frameLayout ->
                try {
                    val act = (context as? Activity) ?: context.findActivity()
                    if (act != null) {
                        LevelPlayManager.updateCurrentActivity(act)
                    }
                    val banner = LevelPlayManager.getOrCreateBannerView(act ?: context)
                    if (banner != null && banner.parent != frameLayout) {
                        (banner.parent as? ViewGroup)?.removeView(banner)
                        frameLayout.removeAllViews()
                        try {
                            banner.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        } catch (_: Throwable) {}
                        frameLayout.addView(banner)
                    }
                } catch (e: Throwable) {
                    Log.w("LevelPlayBannerAd", "Notice updating banner view: ${e.message}")
                }
            }
        )
    }
}
