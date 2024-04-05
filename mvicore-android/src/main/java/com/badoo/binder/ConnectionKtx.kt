package com.badoo.binder

import com.badoo.binder.connector.ConnectorKtx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf

data class ConnectionKtx<Out : Any, In : Any>(
    val from: Flow<Out>,
    val to: (In) -> Unit,
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

    suspend fun collect() {
        connector(from)
            .collect { input ->
                to.invoke(input)
            }
    }
}

infix fun <Out : Any, In : Any> Pair<Flow<Out>, (In) -> Unit>.using(transformer: (Out) -> In?): ConnectionKtx<Out, In> =
    using { outFlow: Flow<Out> ->
        outFlow.flatMapConcat { out ->
            val input = transformer(out)
            if (input != null) {
                flowOf(input)
            } else {
                emptyFlow()
            }
        }
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
