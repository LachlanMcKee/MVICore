package com.badoo.mvicore.feature

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import com.badoo.mvicore.element.ActorKtx
import com.badoo.mvicore.element.NewsPublisher
import com.badoo.mvicore.element.PostProcessor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.feature.PostProcessorTestFeatureKtx.Effect
import com.badoo.mvicore.feature.PostProcessorTestFeatureKtx.News
import com.badoo.mvicore.feature.PostProcessorTestFeatureKtx.State
import com.badoo.mvicore.feature.PostProcessorTestFeatureKtx.Wish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BaseFeatureKtxPostProcessorTest {
    @Test
    fun `GIVEN feature scheduler provided AND InitialTrigger sent WHEN post processor sends PostProcessorTrigger THEN news is in wish order`() =
        runTest {
            val testScope = CoroutineScope(coroutineContext + Job())
            turbineScope {
                val feature = PostProcessorTestFeatureKtx(testScope)
                val turbine: ReceiveTurbine<News> = feature.news.testIn(testScope)

                feature.invoke(Wish.InitialTrigger)

                assertEquals(News.TriggerNews, turbine.awaitItem())
                assertEquals(News.PostProcessorNews, turbine.awaitItem())
                turbine.ensureAllEventsConsumed()
            }
        }
}

private class PostProcessorTestFeatureKtx(coroutineScope: CoroutineScope) :
    BaseFeatureKtx<Wish, Wish, Effect, State, News>(
        coroutineScope = coroutineScope,
        actor = ActorImpl(),
        initialState = State,
        reducer = ReducerImpl(),
        wishToAction = { it },
        newsPublisher = NewsPublisherImpl(),
        postProcessor = PostProcessorImpl(),
    ) {

    sealed class Wish {
        data object InitialTrigger : Wish()
        data object PostProcessorTrigger : Wish()
    }

    sealed class Effect {
        data object TriggerEffect : Effect()
        data object PostProcessorEffect : Effect()
    }

    object State

    sealed class News {
        data object TriggerNews : News()
        data object PostProcessorNews : News()
    }

    class ActorImpl : ActorKtx<State, Wish, Effect> {
        override fun invoke(state: State, wish: Wish): Flow<Effect> = flow {
            when (wish) {
                is Wish.InitialTrigger -> emit(Effect.TriggerEffect)
                is Wish.PostProcessorTrigger -> emit(Effect.PostProcessorEffect)
            }
        }
    }

    class ReducerImpl : Reducer<State, Effect> {
        override fun invoke(state: State, effect: Effect): State = state
    }

    class NewsPublisherImpl : NewsPublisher<Wish, Effect, State, News> {
        override fun invoke(action: Wish, effect: Effect, state: State): News =
            when (effect) {
                is Effect.TriggerEffect -> News.TriggerNews
                is Effect.PostProcessorEffect -> News.PostProcessorNews
            }
    }

    class PostProcessorImpl : PostProcessor<Wish, Effect, State> {
        override fun invoke(action: Wish, effect: Effect, state: State): Wish? =
            if (action is Wish.InitialTrigger) {
                Wish.PostProcessorTrigger
            } else {
                null
            }
    }
}
