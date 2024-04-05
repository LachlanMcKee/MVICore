package com.badoo.binder.middleware.base

import com.badoo.binder.ConnectionKtx

interface MiddlewareKtx<Out : Any, In : Any> : (suspend (In) -> Unit) {

    fun onBind(connection: ConnectionKtx<Out, In>)

    fun onElement(connection: ConnectionKtx<Out, In>, element: In)

    fun onComplete(connection: ConnectionKtx<Out, In>)
}
