package com.badoo.mvicore.feature

import com.badoo.binder.middleware.wrapWithMiddlewareKtx
import com.badoo.mvicore.element.ActorKtx
import com.badoo.mvicore.element.BootstrapperKtx
import com.badoo.mvicore.element.NewsPublisher
import com.badoo.mvicore.element.PostProcessor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.element.WishToAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class BaseFeatureKtx<Wish : Any, in Action : Any, in Effect : Any, State : Any, News : Any>(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    initialState: State,
    bootstrapper: BootstrapperKtx<Action>? = null,
    private val wishToAction: WishToAction<Wish, Action>,
    actor: ActorKtx<State, Action, Effect>,
    reducer: Reducer<State, Effect>,
    postProcessor: PostProcessor<Action, Effect, State>? = null,
    newsPublisher: NewsPublisher<Action, Effect, State, News>? = null,
) : FeatureKtx<Wish, State, News> {

    private val actionFlow: MutableSharedFlow<Action> = MutableSharedFlow()
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(initialState)
    private val newsFlow: MutableSharedFlow<News> = MutableSharedFlow()

    private val postProcessorWrapper: (suspend (Triple<Action, Effect, State>) -> Unit)? =
        postProcessor?.let {
            PostProcessorWrapper(
                postProcessor = postProcessor,
                actionFlow = actionFlow
            ).wrapWithMiddlewareKtx(wrapperOf = postProcessor)
        }

    private val newsPublisherWrapper: (suspend (Triple<Action, Effect, State>) -> Unit)? =
        newsPublisher?.let {
            NewsPublisherWrapper(
                newsPublisher = newsPublisher,
                newsFlow = newsFlow
            ).wrapWithMiddlewareKtx(wrapperOf = postProcessor)
        }

    private val actorWrapper = ActorWrapper(
        coroutineScope = coroutineScope,
        actor = actor,
        stateFlow = stateFlow,
        reducer = ReducerWrapper(
            reducer = reducer,
            stateFlow = stateFlow,
            postProcessor = postProcessorWrapper,
            newsPublisher = newsPublisherWrapper,
        ).wrapWithMiddlewareKtx(),
    ).wrapWithMiddlewareKtx()

    init {
        coroutineScope.launch {
            actionFlow.collect(actorWrapper::invoke)
        }
        coroutineScope.launch {
            if (bootstrapper != null) {
                bootstrapper().collect { action ->
                    actionFlow.emit(action)
                }
            }
        }
    }

    override suspend fun collect(collector: FlowCollector<State>) {
        stateFlow.collect(collector)
    }

    override val news: Flow<News>
        get() = newsFlow

    override val state: State
        get() = stateFlow.value

    override fun invoke(wish: Wish) {
        coroutineScope.launch {
            actionFlow.emit(wishToAction.invoke(wish))
        }
    }

    override fun cancel() {
        coroutineScope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class ActorWrapper<State : Any, Action : Any, Effect : Any>(
        coroutineScope: CoroutineScope,
        private val actor: ActorKtx<State, Action, Effect>,
        private val stateFlow: StateFlow<State>,
        private val reducer: (suspend (Triple<State, Action, Effect>) -> Unit),
    ) : (suspend (Action) -> Unit) {
        private val actorChannel = Channel<Action>()

        init {
            coroutineScope.launch {
                actorChannel
                    .consumeAsFlow()
                    .flatMapConcat { action ->
                        actor.invoke(stateFlow.value, action).map { effect -> action to effect }
                    }
                    .collect { (action, effect) ->
                        if (reducer is ReducerWrapper) {
                            // there's no middleware around it, so we can optimise here by not creating any extra objects
                            reducer.processEffect(stateFlow.value, action, effect)

                        } else {
                            // there are middlewares around it, and we must treat it as Consumer
                            reducer.invoke(Triple(stateFlow.value, action, effect))
                        }
                    }
            }
        }

        override suspend fun invoke(action: Action) {
            actorChannel.send(action)
        }
    }

    private class ReducerWrapper<State : Any, Action : Any, Effect : Any>(
        private val reducer: Reducer<State, Effect>,
        private val stateFlow: MutableStateFlow<State>,
        private val postProcessor: (suspend (Triple<Action, Effect, State>) -> Unit)?,
        private val newsPublisher: (suspend (Triple<Action, Effect, State>) -> Unit)?
    ) : (suspend (Triple<State, Action, Effect>) -> Unit) {

        override suspend fun invoke(t: Triple<State, Action, Effect>) {
            val (state, action, effect) = t
            processEffect(state, action, effect)
        }

        suspend fun processEffect(state: State, action: Action, effect: Effect) {
            val newState = reducer.invoke(state, effect)
            stateFlow.update { newState }
            invokePostProcessor(action, effect, newState)
            invokeNewsPublisher(action, effect, newState)
        }

        private suspend fun invokePostProcessor(action: Action, effect: Effect, state: State) {
            postProcessor?.also {
                if (postProcessor is PostProcessorWrapper) {
                    // there's no middleware around it, so we can optimise here by not creating any extra objects
                    postProcessor.postProcess(action, effect, state)

                } else {
                    // there are middlewares around it, and we must treat it as Consumer
                    postProcessor.invoke(Triple(action, effect, state))
                }
            }
        }

        private suspend fun invokeNewsPublisher(action: Action, effect: Effect, state: State) {
            newsPublisher?.also {
                if (newsPublisher is NewsPublisherWrapper<Action, Effect, State, *>) {
                    // there's no middleware around it, so we can optimise here by not creating any extra objects
                    newsPublisher.publishNews(action, effect, state)

                } else {
                    // there are middlewares around it, and we must treat it as Consumer
                    newsPublisher.invoke(Triple(action, effect, state))
                }
            }
        }
    }

    private class PostProcessorWrapper<Action : Any, Effect : Any, State : Any>(
        private val postProcessor: PostProcessor<Action, Effect, State>,
        private val actionFlow: MutableSharedFlow<Action>
    ) : (suspend (Triple<Action, Effect, State>) -> Unit) {

        override suspend fun invoke(t: Triple<Action, Effect, State>) {
            val (action, effect, state) = t
            postProcess(action, effect, state)
        }

        suspend fun postProcess(action: Action, effect: Effect, state: State) {
            postProcessor.invoke(action, effect, state)?.let { postProcessorAction ->
                actionFlow.emit(postProcessorAction)
            }
        }
    }

    private class NewsPublisherWrapper<Action : Any, Effect : Any, State : Any, News : Any>(
        private val newsPublisher: NewsPublisher<Action, Effect, State, News>,
        private val newsFlow: MutableSharedFlow<News>
    ) : (suspend (Triple<Action, Effect, State>) -> Unit) {

        override suspend fun invoke(t: Triple<Action, Effect, State>) {
            val (action, effect, state) = t
            publishNews(action, effect, state)
        }

        suspend fun publishNews(action: Action, effect: Effect, state: State) {
            newsPublisher.invoke(action, effect, state)?.let { news ->
                newsFlow.emit(news)
            }
        }
    }
}