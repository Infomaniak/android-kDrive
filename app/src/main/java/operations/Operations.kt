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
package operations

import kotlinx.coroutines.CancellationException

inline fun <Response, Result> runOperation(
    handler: OperationHandler<Response, Result>,
    operation: () -> Response
): Result = try {
    handler.onStart()
    val response = operation()
    handler.onResponse(response)
} catch (t: Throwable) {
    when (t) {
        is CancellationException -> {
            handler.onCancellation()
            throw t
        }
        else -> handler.onException(t)
    }
}

infix fun <Response, Result> OperationHandler<Response, Unit>.then(
    secondHandler: OperationHandler<Response, Result>
): OperationHandler<Response, Result> {
    val firstHandler = this
    return object : OperationHandler<Response, Result> {

        override fun onStart() {
            firstHandler.onStart()
            secondHandler.onStart()
        }

        override fun onResponse(response: Response): Result {
            firstHandler.onResponse(response)
            return secondHandler.onResponse(response)
        }

        override fun onException(t: Throwable): Result {
            firstHandler.onException(t)
            return secondHandler.onException(t)
        }

        override fun onCancellation() {
            firstHandler.onCancellation()
            secondHandler.onCancellation()
        }
    }
}
