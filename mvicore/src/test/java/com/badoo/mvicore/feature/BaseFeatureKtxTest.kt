package com.badoo.mvicore.feature

import app.cash.turbine.Event
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.TurbineContext
import app.cash.turbine.turbineScope
import com.badoo.mvicore.TestHelper
import com.badoo.mvicore.TestHelper.Companion.conditionalMultiplier
import com.badoo.mvicore.TestHelper.Companion.initialCounter
import com.badoo.mvicore.TestHelper.Companion.instantFulfillAmount1
import com.badoo.mvicore.TestHelper.TestNews
import com.badoo.mvicore.TestHelper.TestState
import com.badoo.mvicore.TestHelper.TestWish
import com.badoo.mvicore.TestHelper.TestWish.FulfillableAsync
import com.badoo.mvicore.TestHelper.TestWish.FulfillableInstantly1
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWish1
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWish2
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWish3
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWishInitial
import com.badoo.mvicore.TestHelper.TestWish.MaybeFulfillable
import com.badoo.mvicore.TestHelper.TestWish.TranslatesTo3Effects
import com.badoo.mvicore.TestHelper.TestWish.Unfulfillable
import com.badoo.mvicore.utils.RxErrorRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RxErrorRule::class)
class BaseFeatureKtxTest {
    private lateinit var feature: FeatureKtx<TestWish, TestState, TestNews>

    private fun initFeature(scope: CoroutineScope) {
        feature = BaseFeatureKtx(
            coroutineScope = scope,
            initialState = TestState(),
            bootstrapper = TestHelper.TestEmptyBootstrapperKtx(),
            wishToAction = { wish -> wish },
            actor = TestHelper.TestActorKtx(),
            reducer = TestHelper.TestReducer(),
            newsPublisher = TestHelper.TestNewsPublisher(),
        )
    }

    @Test
    fun `emitted initial state is correct`() =
        testFeatureStates { stateTurbine, _ ->
            assertEquals(TestState(), stateTurbine.awaitItem())
        }

    @Test
    fun `there should be no state emission besides the initial one for unfulfillable wishes`() =
        testFeatureStates { stateTurbine, _ ->
            feature.invoke(Unfulfillable)
            feature.invoke(Unfulfillable)
            feature.invoke(Unfulfillable)

            assertEquals(TestState(), stateTurbine.awaitItem())
        }

    @Test
    fun `there should be the same amount of states as wishes that translate 1 - 1 to effects plus one for initial state`() =
        testFeatureStates { stateTurbine, _ ->
            val wishes = listOf<TestWish>(
                // all of them are mapped to 1 effect each
                FulfillableInstantly1,
                FulfillableInstantly1,
                FulfillableInstantly1
            )

            wishes.forEach { feature.invoke(it) }

            stateTurbine.skipItems(1)
            assertEquals(TestState(counter = 102), stateTurbine.awaitItem())
            assertEquals(TestState(counter = 104), stateTurbine.awaitItem())
            assertEquals(TestState(counter = 106), stateTurbine.awaitItem())
        }

    @Test
    fun `there should be 3 times as many states as wishes that translate 1 - 3 to effects plus one for initial state`() =
        testFeatureStates { stateTurbine, _ ->
            val wishes = listOf<TestWish>(
                TranslatesTo3Effects,
                TranslatesTo3Effects,
                TranslatesTo3Effects
            )

            wishes.forEach { feature.invoke(it) }

            stateTurbine.skipItems(1)
        }

    @Test
    fun `last state correctly reflects expected changes in simple case`() =
        testFeatureStates { stateTurbine, _ ->
            val wishes = listOf<TestWish>(
                FulfillableInstantly1,
                FulfillableInstantly1,
                FulfillableInstantly1
            )

            wishes.forEach { feature.invoke(it) }

            stateTurbine.skipItems(3)
            val lastState = stateTurbine.awaitItem()
            assertEquals(initialCounter + wishes.size * instantFulfillAmount1, lastState.counter)
            assertEquals(false, lastState.loading)
        }

    @Test
    fun `intermediate state matches expectations in async case`() =
        testFeatureStates { stateTurbine, _ ->
            val wishes = listOf(
                FulfillableAsync(0)
            )

            wishes.forEach { feature.invoke(it) }

            stateTurbine.skipItems(1)
            val intermediateState = stateTurbine.awaitItem()
            assertEquals(true, intermediateState.loading)
            assertEquals(initialCounter, intermediateState.counter)
            stateTurbine.skipItems(1)
        }

    @Test
    fun `final state matches expectations in async case`() =
        testFeatureStates { stateTurbine, _ ->
            val mockServerDelayMs: Long = 10

            val wishes = listOf(
                FulfillableAsync(mockServerDelayMs)
            )

            wishes.forEach { feature.invoke(it) }

            // Must wait until the loading state has started, otherwise the timer is advanced too soon.
            stateTurbine.skipItems(2)
            val lastState = stateTurbine.awaitItem()
            assertEquals(false, lastState.loading)
            assertEquals(initialCounter + TestHelper.delayedFulfillAmount, lastState.counter)
        }

    @Test
    fun `the number of state emissions should reflect the number of effects plus one for initial state in complex case`() =
        testFeatureStates { stateTurbine, _ ->
            val wishes = listOf(
                FulfillableInstantly1,  // 1 state change
                FulfillableInstantly1,  // 1 state change
                MaybeFulfillable,       // 0 state changes
                Unfulfillable,          // 0 state changes
                FulfillableInstantly1,  // 1 state change
                FulfillableInstantly1,  // 1 state change
                MaybeFulfillable,       // 1 in this case
                TranslatesTo3Effects    // 0 state changes
            )

            wishes.forEach { feature.invoke(it) }

            stateTurbine.skipItems(6) // 5 + 1 (for the initial state)
        }

    @Test
    fun `last state correctly reflects expected changes in complex case`() =
        testFeatureStates { stateTurbine, _ ->
            val wishes = listOf(
                FulfillableInstantly1,  // should increase +2 (total: 102)
                FulfillableInstantly1,  // should increase +2 (total: 104)
                MaybeFulfillable,       // should not do anything in this state, as total of 2 is not divisible by 3
                Unfulfillable,          // should not do anything
                FulfillableInstantly1,  // should increase +2 (total: 106)
                FulfillableInstantly1,  // should increase +2 (total: 108)
                MaybeFulfillable,       // as total of 108 is divisible by 3, it should multiply by *10 (total: 1080)
                TranslatesTo3Effects    // should not affect state
            )

            wishes.forEach { feature.invoke(it) }

            stateTurbine.skipItems(5)
            val lastState = stateTurbine.awaitItem()
            assertEquals(
                (initialCounter + 4 * instantFulfillAmount1) * conditionalMultiplier,
                lastState.counter
            )
            assertEquals(false, lastState.loading)
        }

    @Test
    fun `loopback from news to multiple wishes has access to correct latest state`() =
        testFeatureStates { stateTurbine, testScope ->
            testScope.launch {
                feature.news.collect {
                    if (it === TestNews.Loopback) {
                        feature.invoke(LoopbackWish2)
                        feature.invoke(LoopbackWish3)
                    }
                }
            }

            feature.invoke(LoopbackWishInitial)
            feature.invoke(LoopbackWish1)

            stateTurbine.skipItems(1)
            assertEquals("Loopback initial state", stateTurbine.awaitItem().id)
            assertEquals("Loopback state 1", stateTurbine.awaitItem().id)
            assertEquals("Loopback state 2", stateTurbine.awaitItem().id)
            assertEquals("Loopback state 3", stateTurbine.awaitItem().id)
        }

    private fun testFeatureStates(func: suspend TurbineContext.(ReceiveTurbine<TestState>, CoroutineScope) -> Unit) =
        runTest {
            val testScope = CoroutineScope(coroutineContext + Job())
            turbineScope {
                initFeature(testScope)

                val turbine: ReceiveTurbine<TestState> = feature.testIn(testScope)
                println("turbine started")
                func(turbine, testScope)

                val remainingEvents = turbine.cancelAndConsumeRemainingEvents()
                assertEquals(emptyList<Event<TestState>>(), remainingEvents)
            }
        }
}
