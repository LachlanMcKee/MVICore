package com.badoo.mvicore.feature

import io.reactivex.Scheduler

/**
 * A set of [Scheduler]s that change the threading behaviour of [BaseFeature]
 */
sealed interface FeatureScheduler {
    val scheduler: Scheduler
    val shouldSubscribeOnScheduler: Boolean

    /**
     * Wraps RxJava scheduler and cannot avoid adding work to the end of the queue.
     * Please ensure that you use a single threaded Scheduler otherwise you may encounter issues.
     */
    data class Simple(
        override val scheduler: Scheduler
    ) : FeatureScheduler {
        override val shouldSubscribeOnScheduler: Boolean = true
    }

    /**
     * Wraps RxJava scheduler and can avoid adding work to the end of the queue by checking whether
     * the feature is already on the correct thread.
     *
     * Please ensure that you use a single threaded Scheduler otherwise you may encounter issues.
     */
    interface Smart : FeatureScheduler {
        /**
         * The scheduler that this feature executes on.
         * This must be single threaded, otherwise your feature will be non-deterministic.
         */
        override val scheduler: Scheduler

        override val shouldSubscribeOnScheduler: Boolean
            get() = !isOnSchedulerThread

        /**
         * Helps avoid sending a message to a thread if we are already on the thread.
         */
        val isOnSchedulerThread: Boolean
    }
}
