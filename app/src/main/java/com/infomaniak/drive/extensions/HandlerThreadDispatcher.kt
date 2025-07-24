/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.extensions

import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Creates an internal [HandlerThread] that will be used to run code from this [kotlinx.coroutines.CoroutineDispatcher].
 *
 * Note that this is a closable resource, and closing it will call [HandlerThread.quitSafely].
 */
@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
class HandlerThreadDispatcher(
    name: String,
    priority: Int = Process.THREAD_PRIORITY_DEFAULT
) : CloseableCoroutineDispatcher() {

    private val handlerThread = HandlerThread(name, priority).also { it.start() }

    private val handler = if (SDK_INT >= 28) Handler.createAsync(handlerThread.looper) else try {
        Handler::class.java.getDeclaredConstructor(
            Looper::class.java,
            Handler.Callback::class.java,
            Boolean::class.javaPrimitiveType // async
        ).newInstance(handlerThread.looper, null, true)
    } catch (_: NoSuchMethodException) {
        Handler(handlerThread.looper) // Hidden constructor absent. Fall back to non-async constructor.
    }

    private val dispatcher = handler.asCoroutineDispatcher(name = name)

    override val executor: Executor = Executor { dispatch(EmptyCoroutineContext, it) }

    override fun dispatch(context: CoroutineContext, block: Runnable) = dispatcher.dispatch(context, block)

    override fun close() {
        handlerThread.quitSafely()
    }
}
