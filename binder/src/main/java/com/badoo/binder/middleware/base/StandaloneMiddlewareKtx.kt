package com.badoo.binder.middleware.base

import com.badoo.binder.ConnectionKtx

internal class StandaloneMiddlewareKtx<In : Any>(
    private val wrappedMiddleware: MiddlewareKtx<In, In>,
    name: String? = null,
    postfix: String? = null
) : AbstractMiddlewareKtx<In, In>(wrappedMiddleware) {

    private var bound = false

    //    private var disposed = false
    private val connection = ConnectionKtx(
        to = innerMost,
        name = "${name ?: innerMost.javaClass.canonicalName}.${postfix ?: "input"}",
        connector = { it },
    )

    init {
        onBind(connection)
    }

    override fun onBind(connection: ConnectionKtx<In, In>) {
        assertSame(connection)

        bound = true
        wrappedMiddleware.onBind(connection)
    }

    override suspend fun invoke(input: In) {
        wrappedMiddleware.onElement(connection, input)
        wrappedMiddleware.invoke(input)
    }

    override fun onComplete(connection: ConnectionKtx<In, In>) {
        wrappedMiddleware.onComplete(connection)
    }

//    override fun isDisposed() = disposed
//
//    override fun dispose() {
//        onComplete(this.connection)
//        disposed = true
//    }

    private fun assertSame(connection: ConnectionKtx<In, In>) {
        if (bound && connection != this.connection) {
            throw IllegalStateException("Middleware was initialised in standalone mode, can't accept other connections")
        }
    }

}
