package com.badoo.mvicore.feature

import com.badoo.mvicore.element.Actor
import com.badoo.mvicore.element.Bootstrapper
import com.badoo.mvicore.element.NewsPublisher
import com.badoo.mvicore.element.PostProcessor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.element.WishToAction
import io.reactivex.ObservableSource
import io.reactivex.Observer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.asObservable

/**
 * A base implementation of a single threaded feature.
 *
 * Please be aware of the following threading behaviours based on whether a 'featureScheduler' is provided.
 *
 * No 'featureScheduler' provided:
 * The feature must execute on the thread that created the class. If the bootstrapper/actor observables
 * change to a different thread it is your responsibility to switch back to the feature's original
 * thread via observeOn, otherwise an exception will be thrown.
 *
 * 'featureScheduler' provided (this must be single threaded):
 * The feature does not have to execute on the thread that created the class. It automatically
 * switches to the feature scheduler thread when necessary.
 */
val BaseFeatureInteropImmediateDispatcherDefault: CoroutineDispatcher = Dispatchers.Main.immediate
var BaseFeatureInteropImmediateDispatcher: CoroutineDispatcher = BaseFeatureInteropImmediateDispatcherDefault

open class BaseFeature<Wish : Any, in Action : Any, in Effect : Any, State : Any, News : Any>(
    initialState: State,
    bootstrapper: Bootstrapper<Action>? = null,
    wishToAction: WishToAction<Wish, Action>,
    actor: Actor<State, Action, Effect>,
    reducer: Reducer<State, Effect>,
    postProcessor: PostProcessor<Action, Effect, State>? = null,
    newsPublisher: NewsPublisher<Action, Effect, State, News>? = null,
    private val featureScheduler: FeatureScheduler? = null
) : Feature<Wish, State, News> {

    private val featureDelegate = BaseFeatureKtx(
        coroutineScope = CoroutineScope(
            featureScheduler?.scheduler?.asCoroutineDispatcher()
                ?: BaseFeatureInteropImmediateDispatcher
        ),
        initialState = initialState,
        bootstrapper = bootstrapper?.let {
            {
                bootstrapper.invoke().asFlow()
            }
        },
        wishToAction = wishToAction,
        actor = { state, action ->
            actor.invoke(state, action).asFlow()
        },
        reducer = reducer,
        postProcessor = postProcessor,
        newsPublisher = newsPublisher
    )

    override val news: ObservableSource<News>
        get() = featureDelegate.news.asObservable()

    override val state: State
        get() = featureDelegate.state

    override fun dispose() {
        featureDelegate.cancel()
    }

    override fun isDisposed(): Boolean {
        return false // TODO
    }

    override fun subscribe(observer: Observer<in State>) {
        featureDelegate.asObservable().subscribe(observer)
    }

    override fun accept(wish: Wish) {
        featureDelegate.invoke(wish)
    }
}
