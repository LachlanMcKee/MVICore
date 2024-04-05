package com.badoo.binder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BinderKtx {
    fun bindScope(scope: CoroutineScope, func: BindScope.() -> Unit) {
        BindScope(scope).bindScope(scope, func)
    }

    class BindScope(private val scope: CoroutineScope) {
        private val pendingConnections: MutableList<ConnectionKtx<*, *>> = mutableListOf()

        fun <T : Any> bind(connection: Pair<Flow<T>, (T) -> Unit>) {
            bind(
                ConnectionKtx(
                    from = connection.first,
                    to = connection.second,
                    connector = { it }
                )
            )
        }

        fun <Out : Any, In : Any> bind(connection: ConnectionKtx<Out, In>) {
            pendingConnections.add(connection)
        }

        private fun bindAll() = runBlocking {
            val localPendingConnections = pendingConnections.toMutableList()
            pendingConnections.clear()

            scope.launch {
                localPendingConnections.forEach { connection ->
                    connection.collect()
                }
            }
        }

        fun bindScope(scope: CoroutineScope, func: BindScope.() -> Unit) {
            val bindScope = BindScope(scope)
            func(bindScope)
            bindScope.bindAll()
        }
    }
}
