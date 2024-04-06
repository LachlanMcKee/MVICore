package com.badoo.binder.middleware.config

import com.badoo.binder.middleware.base.Middleware
import com.badoo.binder.middleware.base.MiddlewareKtx
import io.reactivex.functions.Consumer

interface ConsumerMiddlewareFactory<T : Any> : (Consumer<T>) -> Middleware<Any, T>
interface ConsumerMiddlewareFactoryKtx<T : Any> : (suspend (T) -> Unit) -> MiddlewareKtx<Any, T>

fun <T : Any> ((Consumer<T>) -> Middleware<Any, T>).toFactory() =
    object : ConsumerMiddlewareFactory<T> {
        override fun invoke(p1: Consumer<T>): Middleware<Any, T> =
            this@toFactory.invoke(p1)
    }

fun <T : Any> ((suspend (T) -> Unit) -> MiddlewareKtx<Any, T>).toFactory() =
    object : ConsumerMiddlewareFactoryKtx<T> {
        override fun invoke(p1: suspend (T) -> Unit): MiddlewareKtx<Any, T> =
            this@toFactory.invoke(p1)
    }
