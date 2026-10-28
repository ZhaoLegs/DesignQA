package com.legs.specinspector

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Debug-only bridge for the Spec design-QA tool.
 *
 * content://<applicationId>.specinspector/dump      -> every visible view in every tracked window
 * content://<applicationId>.specinspector/at?x=&y=  -> views containing the screen point (deepest last)
 *
 * Result: one row, column "json". Only the Spec app (com.legs.designqa) or this app itself may query.
 * Everything is wrapped so the host app can never crash because of this library.
 */
class SpecInspectorProvider : ContentProvider() {
    companion object {
        const val SPEC_PACKAGE = "com.legs.designqa"
        internal val activities = ArrayList<WeakReference<Activity>>()
        private const val TIMEOUT_MS = 1500L
    }

    override fun onCreate(): Boolean {
        runCatching {
            val app = context?.applicationContext as? Application ?: return true
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) { synchronized(activities) { activities.removeAll { it.get() == null || it.get() === a }; activities += WeakReference(a) } }
                override fun onActivityDestroyed(a: Activity) { synchronized(activities) { activities.removeAll { it.get() == null || it.get() === a } } }
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            })
        }
        return true
    }

    private fun allowed(): Boolean = runCatching {
        if (Binder.getCallingUid() == Process.myUid()) return true
        val pm = context!!.packageManager
        val pkgs = pm.getPackagesForUid(Binder.getCallingUid()) ?: return false
        SPEC_PACKAGE in pkgs
    }.getOrDefault(false)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (!allowed()) return null
        val json = runCatching {
            val x = uri.getQueryParameter("x")?.toIntOrNull()
            val y = uri.getQueryParameter("y")?.toIntOrNull()
            onMain { Inspector.dump(context!!, if (uri.lastPathSegment == "at" && x != null && y != null) intArrayOf(x, y) else null) }
        }.getOrNull() ?: "{\"error\":\"unavailable\"}"
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json)) }
    }

    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        var out: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { try { out = runCatching(block).getOrNull() } finally { latch.countDown() } }
        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return out
    }

    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
