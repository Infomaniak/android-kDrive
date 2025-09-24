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
interface OperationHandler<Response, Result> {
    fun onStart() = Unit
    fun onCancellation() = Unit
    fun onException(t: Throwable): Result
    fun onResponse(response: Response): Result

    companion object {
        operator fun <Response, Result> invoke(
            onStart: () -> Unit = {},
            onCancellation: () -> Unit = {},
            onException: (t: Throwable) -> Result,
            onResponse: (response: Response) -> Result,
        ): OperationHandler<Response, Result> {
            return object : OperationHandler<Response, Result> {
                override fun onStart() = onStart()
                override fun onCancellation() = onCancellation()
                override fun onException(t: Throwable) = onException(t)
                override fun onResponse(response: Response) = onResponse(response)
            }
        }
    }
}
