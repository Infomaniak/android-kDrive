/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
package com.infomaniak.drive.utils

import com.google.gson.*
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.utils.CustomDateTypeAdapter
import java.lang.reflect.Type
import java.util.*

object FileDeserializer : JsonDeserializer<File> {

    private var gson: Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, CustomDateTypeAdapter())
        .create()

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): File =
        gson.fromJson(json, File::class.java)

}
