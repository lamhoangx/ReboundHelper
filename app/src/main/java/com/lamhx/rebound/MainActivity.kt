package com.lamhx.rebound

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.lamhx.rebound.helper.EdgeHooksReboundView

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var container = bind<ViewGroup>(R.id.main_act)

        var leftRightEdgeHooks = EdgeHooksReboundView(baseContext)
        leftRightEdgeHooks.setBackgroundColor(Color.DKGRAY)
        container.addView(leftRightEdgeHooks, 50.px, 30.px)

    }

    fun <T : View> Activity.bind(@IdRes res: Int): T {
        @Suppress("UNCHECKED_CAST")
        return findViewById(res) as T
    }

    val Int.dp: Int
        get() = (this / Resources.getSystem().displayMetrics.density).toInt()
    val Int.px: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()
}
