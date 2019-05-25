/*
 * Copyright © 2019 Wren Powell
 *
 * This file is part of Reversion.
 *
 * Reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.gui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import kotlin.coroutines.CoroutineContext

/**
 * An action to be taken by an actor.
 */
sealed class ActorAction<T>

/**
 * An action which sends the given [element] to the actor.
 */
data class SendAction<T>(val element: T) : ActorAction<T>()

/**
 * An action which waits for the actor to finish processing all previously received actions.
 *
 * @param [acknowledgment] A future that completes and returns `true` when all previously received actions have been
 * processed.
 */
data class FlushAction<T>(val acknowledgment: CompletableDeferred<Boolean>) : ActorAction<T>()

/**
 * An actor that can be flushed using the [flush] method.
 */
@UseExperimental(ObsoleteCoroutinesApi::class)
fun <T> CoroutineScope.flushableActor(
    context: CoroutineContext = Dispatchers.Default,
    capacity: Int = Channel.UNLIMITED,
    block: suspend (T) -> Unit
): SendChannel<ActorAction<T>> {
    return actor(context = context, capacity = capacity) {
        for (action in channel) {
            when (action) {
                is SendAction -> block(action.element)
                is FlushAction -> action.acknowledgment.complete(true)
            }
        }
    }
}

/**
 * Adds the given [element] to the channel as a [SendAction] with [sendBlocking].
 */
fun <T> SendChannel<ActorAction<T>>.sendBlocking(element: T) {
    sendBlocking(SendAction(element))
}

/**
 * Waits for the actor to finish processing all previously received actions.
 */
@UseExperimental(ExperimentalCoroutinesApi::class)
suspend fun <T> SendChannel<ActorAction<T>>.flush() {
    val acknowledgement = CompletableDeferred<Boolean>()
    // TODO: Fix [IllegalStateException] when a handler has already been registered.
    invokeOnClose { cause ->
        acknowledgement.cancel(CancellationException("The actor's channel closed.", cause))
    }
    send(FlushAction(acknowledgement))
    acknowledgement.await()
}
