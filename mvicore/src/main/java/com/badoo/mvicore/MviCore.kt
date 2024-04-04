package com.badoo.mvicore

import com.badoo.mvicore.feature.FeatureThreadStrategy
import com.badoo.mvicore.feature.FeatureThreadStrategy.ExecuteOnCurrentThread
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configurable properties for MVICore.
 */
object MviCore {
    /**
     * Used by all features unless an explicit thread strategy is provided.
     */
    private var defaultFeatureThreadStrategy: FeatureThreadStrategy = ExecuteOnCurrentThread
    private val configurationLocked = AtomicBoolean(false)

    fun getDefaultFeatureThreadStrategy(): FeatureThreadStrategy {
        lockConfiguration()
        return defaultFeatureThreadStrategy
    }

    fun setDefaultFeatureThreadStrategy(threadStrategy: FeatureThreadStrategy) {
        if (!configurationLocked.get()) {
            defaultFeatureThreadStrategy = threadStrategy
        }
    }

    /**
     * Once the configuration is accessed for the first time, it should be locked to avoid
     * inconsistent behaviours.
     */
    private fun lockConfiguration() {
        configurationLocked.set(true)
    }
}