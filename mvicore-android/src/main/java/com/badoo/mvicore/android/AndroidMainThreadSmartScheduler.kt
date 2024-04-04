package com.badoo.mvicore.android

import android.os.Looper
import com.badoo.mvicore.feature.FeatureScheduler
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers

/**
 * A feature scheduler that ensures that MVICore feature only manipulates state on the Android
 * main thread.
 *
 * It also uses the 'isOnFeatureThread' field to avoid observing on the main thread if it is already
 * the current thread.
 */
object AndroidMainThreadSmartScheduler : FeatureScheduler.Smart {
    override val scheduler: Scheduler
        get() = AndroidSchedulers.mainThread()

    override val isOnSchedulerThread: Boolean
        get() = Looper.myLooper() == Looper.getMainLooper()
}
