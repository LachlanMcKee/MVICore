package com.badoo.binder

import com.badoo.binder.connector.Connector
import com.badoo.binder.connector.ConnectorKtx
import io.reactivex.ObservableSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.asObservable

data class ConnectionKtx<Out : Any, In : Any>(
    val from: Flow<Out>? = null,
    val to: suspend (In) -> Unit,
    val connector: ConnectorKtx<Out, In>,
    val name: String? = null,
) {
    companion object {
        private const val ANONYMOUS: String = "anonymous"
    }

    fun isAnonymous(): Boolean =
        name == null

    override fun toString(): String =
        "<${name ?: ANONYMOUS}> (${from} --> $to${connector.let { " using $it" }})"

//    suspend fun collect() {
//        connector(from)
//            .collect { input ->
//                to.invoke(input)
//            }
//    }
}

fun <Out : Any, In : Any> ConnectionKtx<Out, In>.toConnectionRx(): Connection<Out, In> {
    val connectionKtx = this
    return Connection(
        from = connectionKtx.from?.asObservable(),
        to = { runBlocking { connectionKtx.to.invoke(it) } },
        name = connectionKtx.name,
        connector = object : Connector<Out, In> {
            override fun invoke(p1: ObservableSource<out Out>): ObservableSource<In> =
                connectionKtx.connector.invoke(p1.asFlow()).asObservable()
        }
    )
}

infix fun <Out : Any, In : Any> Pair<Flow<Out>, (In) -> Unit>.using(transformer: (Out) -> In?): ConnectionKtx<Out, In> =
    using { outFlow: Flow<Out> ->
        outFlow.mapNotNull { transformer(it) }
    }

infix fun <Out : Any, In : Any> Pair<Flow<Out>, (In) -> Unit>.using(connector: ConnectorKtx<Out, In>): ConnectionKtx<Out, In> =
    ConnectionKtx(
        from = first,
        to = second,
        connector = connector
    )

infix fun <T : Any> Pair<Flow<T>, (T) -> Unit>.named(name: String): ConnectionKtx<T, T> =
    ConnectionKtx(
        from = first,
        to = second,
        name = name,
        connector = { it }
    )

infix fun <Out : Any, In : Any> ConnectionKtx<Out, In>.named(name: String) =
    copy(
        name = name
    )
