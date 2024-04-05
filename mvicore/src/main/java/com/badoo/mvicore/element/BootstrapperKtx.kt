package com.badoo.mvicore.element

import kotlinx.coroutines.flow.Flow

typealias BootstrapperKtx<Action> = () -> Flow<Action>
