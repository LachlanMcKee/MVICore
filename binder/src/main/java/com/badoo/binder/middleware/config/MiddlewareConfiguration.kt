package com.badoo.binder.middleware.config

import com.badoo.binder.ConnectionKtx
import com.badoo.binder.middleware.base.MiddlewareKtx
import com.badoo.binder.toConnectionRx
import io.reactivex.functions.Consumer
import kotlinx.coroutines.runBlocking

data class MiddlewareConfiguration(
    private val condition: WrappingCondition,
    private val factories: List<ConsumerMiddlewareFactory<*>>
) {

    fun <T : Any> applyOn(
        consumerToWrap: Consumer<T>,
        targetToCheck: Any,
        name: String?,
        standalone: Boolean
    ): Consumer<T> {
        var current = consumerToWrap
        val middlewares =
            if (condition.shouldWrap(targetToCheck, name, standalone))
                factories.filterIsInstance<ConsumerMiddlewareFactory<T>>() else listOf()
        middlewares.forEach {
            current = it.invoke(current)
        }

        return current
    }

    fun <T : Any> applyOnKtx(
        consumerToWrap: (suspend (T) -> Unit),
        targetToCheck: Any,
        name: String?,
        standalone: Boolean
    ): (suspend (T) -> Unit) {
        var current = consumerToWrap
        val middlewares: List<ConsumerMiddlewareFactoryKtx<T>> =
            if (condition.shouldWrap(targetToCheck, name, standalone)) {
                factories
                    .filterIsInstance<ConsumerMiddlewareFactory<T>>()
                    .map { rxConsumerFactory ->
                        object : ConsumerMiddlewareFactoryKtx<T> {
                            override fun invoke(p1: suspend (T) -> Unit): MiddlewareKtx<Any, T> {
                                val rxConsumer = rxConsumerFactory.invoke(Consumer<T> { t ->
                                    runBlocking {
                                        p1.invoke(t)
                                    }
                                })
                                return object : MiddlewareKtx<Any, T> {
                                    override fun onBind(connection: ConnectionKtx<Any, T>) {
                                        rxConsumer.onBind(connection.toConnectionRx())
                                    }

                                    override fun onElement(
                                        connection: ConnectionKtx<Any, T>,
                                        element: T
                                    ) {
                                        rxConsumer.onElement(connection.toConnectionRx(), element)
                                    }

                                    override fun onComplete(connection: ConnectionKtx<Any, T>) {
                                        rxConsumer.onComplete(connection.toConnectionRx())
                                    }

                                    override suspend fun invoke(p1: T) {
                                        rxConsumer.accept(p1)
                                    }
                                }
                            }
                        }
                    }
            } else {
                listOf()
            }
        middlewares.forEach {
            current = it.invoke(current)
        }

        return current
    }
}
