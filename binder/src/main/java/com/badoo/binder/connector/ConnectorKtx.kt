package com.badoo.binder.connector

import kotlinx.coroutines.flow.Flow

fun interface ConnectorKtx<Out : Any, In : Any> : (Flow<Out>) -> Flow<In>
