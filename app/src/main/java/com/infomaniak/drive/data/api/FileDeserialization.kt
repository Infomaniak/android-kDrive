/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.data.api

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.api.ApiController
import java.lang.reflect.Type

class FileDeserialization : JsonDeserializer<File> {
    private val gson: Gson = ApiController.gson.newBuilder().create()

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): File {
        return gson.fromJson<File>(json, typeOfT).apply { initUid() }
    }
}
