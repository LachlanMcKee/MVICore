package com.badoo.binder.middleware.base

import com.badoo.binder.ConnectionKtx

abstract class AbstractMiddlewareKtx<Out : Any, In : Any>(
    protected val wrapped: (suspend (In) -> Unit)
) : MiddlewareKtx<Out, In> {

    override fun onBind(connection: ConnectionKtx<Out, In>) {
        wrapped.applyIfMiddleware { onBind(connection) }
    }

    override suspend fun invoke(input: In) {
        wrapped.invoke(input)
    }

    override fun onElement(connection: ConnectionKtx<Out, In>, element: In) {
        wrapped.applyIfMiddleware { onElement(connection, element) }
    }

    override fun onComplete(connection: ConnectionKtx<Out, In>) {
        wrapped.applyIfMiddleware { onComplete(connection) }
    }

    private inline fun (suspend (In) -> Unit).applyIfMiddleware(
        block: AbstractMiddlewareKtx<Out, In>.() -> Unit
    ) {
        if (this is AbstractMiddlewareKtx<*, *>) {
            (this as AbstractMiddlewareKtx<Out, In>).block()
        }
    }

    protected val innerMost: suspend (In) -> Unit by lazy {
        var consumer = wrapped
        while (consumer is AbstractMiddlewareKtx<*, *>) {
            consumer = (consumer as AbstractMiddlewareKtx<Out, In>).wrapped
        }
        consumer
    }
}
