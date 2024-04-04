package com.badoo.mvicore.feature

import com.badoo.mvicore.extension.SameThreadVerifier
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.Observer
import io.reactivex.Scheduler

/**
 * [Feature]'s are single threaded, however a strategy can be supplied to define which thread
 * it operates on, and potentially which thread it is observed on.
 */
sealed interface FeatureThreadStrategy {
    /**
     * Creates a thread verifier that is used to throw an exception if the feature is mutating
     * state on a different thread.
     */
    fun createSameThreadVerifier(featureClass: Class<Feature<*, *, *>>): SameThreadVerifier?

    /**
     * Adds schedulers (if any) to the bootstrapper.
     */
    fun <T> setupBootstrapperSchedulers(observable: Observable<T>): Observable<T>

    /**
     * Optionally moves the observable chain to the feature thread.
     */
    fun <T> observeOnFeatureScheduler(observable: Observable<T>): Observable<T>

    /**
     * Subscribes to the [Feature] states.
     * This could potentially be observed on a different thread to the feature thread.
     */
    fun <State> subscribeToStates(stateObservable: Observable<State>, observer: Observer<in State>)

    /**
     * Returns the [Feature.news]
     * This could potentially be observed on a different thread to the feature thread.
     */
    fun <News> observeNews(news: Observable<News>): ObservableSource<News>

    /**
     * The feature triggers work on the thread that it is currently on.
     *
     * It is up to the developer to ensure that the feature is on the correct thread otherwise the
     * [SameThreadVerifier] will throw an exception (as Features are single threaded).
     */
    data object ExecuteOnCurrentThread : FeatureThreadStrategy {
        override fun createSameThreadVerifier(featureClass: Class<Feature<*, *, *>>) =
            SameThreadVerifier(featureClass)

        override fun <T> setupBootstrapperSchedulers(observable: Observable<T>): Observable<T> =
            observable

        override fun <T> observeOnFeatureScheduler(observable: Observable<T>): Observable<T> =
            observable

        override fun <State> subscribeToStates(
            stateObservable: Observable<State>,
            observer: Observer<in State>
        ) {
            stateObservable.subscribe(observer)
        }

        override fun <News> observeNews(news: Observable<News>): ObservableSource<News> = news
    }

    /**
     * The feature handles moving back to the feature thread internally.
     *
     * When [FeatureScheduler.Simple] is provided the feature will always observe/subscribe
     * on the feature thread which means work will be added at the end of the thread queue.
     *
     * When [FeatureScheduler.Smart] is provided the feature check whether the current thread is the
     * already the feature thread and avoid adding the work at the end of the thread queue.
     *
     * When an observationScheduler is provided then [Feature.news] and [Feature.subscribe] will
     * be observed on this scheduler. To avoid this thread switching you can use
     * [Feature.backgroundNews] and [Feature.backgroundStates] instead.
     *
     * When an observationScheduler is not provided then [Feature.news] and [Feature.subscribe] will
     * not be observed on any scheduler. The [Feature.backgroundNews] and [Feature.backgroundStates]
     * functions are identical to [Feature.news] and [Feature.subscribe] in this scenario.
     */
    data class ExecuteOnFeatureScheduler(
        val featureScheduler: FeatureScheduler,
        val observationScheduler: Scheduler? = null,
    ) : FeatureThreadStrategy {
        override fun createSameThreadVerifier(featureClass: Class<Feature<*, *, *>>) = null

        override fun <T> setupBootstrapperSchedulers(observable: Observable<T>): Observable<T> =
            observeOnFeatureScheduler(observable)
                .subscribeOn(featureScheduler.scheduler)

        override fun <T> observeOnFeatureScheduler(observable: Observable<T>): Observable<T> =
            observable.flatMap { value ->
                val upstream = Observable.just(value)

                if (featureScheduler.shouldSubscribeOnScheduler) {
                    upstream.subscribeOn(featureScheduler.scheduler)
                } else {
                    // Avoid adding the event at the end of the queue.
                    upstream
                }
            }

        override fun <State> subscribeToStates(
            stateObservable: Observable<State>,
            observer: Observer<in State>
        ) {
            observationScheduler.also { scheduler ->
                if (scheduler != null) {
                    stateObservable
                        .observeOn(observationScheduler)
                        .subscribe(observer)
                } else {
                    stateObservable.subscribe(observer)
                }
            }
        }

        override fun <News> observeNews(news: Observable<News>): ObservableSource<News> =
            observationScheduler
                ?.let { scheduler -> news.observeOn(scheduler) }
                ?: news
    }
}