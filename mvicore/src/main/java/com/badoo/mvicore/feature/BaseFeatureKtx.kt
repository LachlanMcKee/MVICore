package com.badoo.mvicore.feature

import com.badoo.mvicore.element.ActorKtx
import com.badoo.mvicore.element.BootstrapperKtx
import com.badoo.mvicore.element.NewsPublisher
import com.badoo.mvicore.element.PostProcessor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.element.WishToAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BaseFeatureKtx<Wish : Any, in Action : Any, in Effect : Any, State : Any, News : Any>(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    initialState: State,
    bootstrapper: BootstrapperKtx<Action>? = null,
    private val wishToAction: WishToAction<Wish, Action>,
    actor: ActorKtx<State, Action, Effect>,
    reducer: Reducer<State, Effect>,
    postProcessor: PostProcessor<Action, Effect, State>? = null,
    newsPublisher: NewsPublisher<Action, Effect, State, News>? = null,
) : FeatureKtx<Wish, State, News> {

    private val mutex = Mutex()
    private val actionFlow: MutableSharedFlow<Action> = MutableSharedFlow()
    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(initialState)
    private val newsFlow: MutableSharedFlow<News> = MutableSharedFlow()

    private val postProcessorWrapper: PostProcessorWrapper<Action, Effect, State>? =
        postProcessor?.let {
            PostProcessorWrapper(
                postProcessor = postProcessor,
                actionFlow = actionFlow
            )
        }

    private val newsPublisherWrapper: NewsPublisherWrapper<Action, Effect, State, News>? =
        newsPublisher?.let {
            NewsPublisherWrapper(
                newsPublisher = newsPublisher,
                newsFlow = newsFlow
            )
        }

    private val actorWrapper = ActorWrapper(
        actor = actor,
        stateFlow = stateFlow,
        reducerWrapper = ReducerWrapper(
            reducer = reducer,
            stateFlow = stateFlow,
            postProcessorWrapper = postProcessorWrapper,
            newsPublisherWrapper = newsPublisherWrapper,
        ),
    )

    init {
        coroutineScope.launch {
            actionFlow.collect { action ->
                // Ensure the actor is called serially to ensure state changes are deterministic.
                mutex.withLock {
                    actorWrapper.processAction(state, action)
                }
            }
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

    private class ActorWrapper<State : Any, Action : Any, Effect : Any>(
        private val actor: ActorKtx<State, Action, Effect>,
        private val stateFlow: StateFlow<State>,
        private val reducerWrapper: ReducerWrapper<State, Action, Effect>,
    ) {
        suspend fun processAction(state: State, action: Action) {
            actor
                .invoke(state, action)
                .collect { effect ->
                    invokeReducer(action, effect)
                }
        }

        private suspend fun invokeReducer(action: Action, effect: Effect) {
            reducerWrapper.processEffect(stateFlow.value, action, effect)
        }
    }

    private class ReducerWrapper<State : Any, Action : Any, Effect : Any>(
        private val reducer: Reducer<State, Effect>,
        private val stateFlow: MutableStateFlow<State>,
        private val postProcessorWrapper: PostProcessorWrapper<Action, Effect, State>?,
        private val newsPublisherWrapper: NewsPublisherWrapper<Action, Effect, State, *>?
    ) {
        suspend fun processEffect(state: State, action: Action, effect: Effect) {
            val newState = reducer.invoke(state, effect)
            stateFlow.emit(newState)
            invokePostProcessor(action, effect, newState)
            invokeNewsPublisher(action, effect, newState)
        }

        private suspend fun invokePostProcessor(action: Action, effect: Effect, state: State) {
            postProcessorWrapper?.postProcess(action, effect, state)
        }

        private suspend fun invokeNewsPublisher(action: Action, effect: Effect, state: State) {
            newsPublisherWrapper?.publishNews(action, effect, state)
        }
    }

    private class PostProcessorWrapper<Action : Any, Effect : Any, State : Any>(
        private val postProcessor: PostProcessor<Action, Effect, State>,
        private val actionFlow: MutableSharedFlow<Action>
    ) {
        suspend fun postProcess(action: Action, effect: Effect, state: State) {
            postProcessor.invoke(action, effect, state)?.let {
                actionFlow.emit(it)
            }
        }
    }

    private class NewsPublisherWrapper<Action : Any, Effect : Any, State : Any, News : Any>(
        private val newsPublisher: NewsPublisher<Action, Effect, State, News>,
        private val newsFlow: MutableSharedFlow<News>
    ) {
        suspend fun publishNews(action: Action, effect: Effect, state: State) {
            newsPublisher.invoke(action, effect, state)?.let {
                newsFlow.emit(it)
            }
        }
    }
}