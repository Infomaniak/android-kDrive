/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.utils.serialization

import com.infomaniak.drive.data.models.TeamDetails
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.MaintenanceType
import io.realm.RealmList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Decodes a JSON array into an unmanaged [RealmList]. It never touches the Realm schema. */
open class RealmListSerializer<T>(elementSerializer: KSerializer<T>) : KSerializer<RealmList<T>> {

    private val listSerializer = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: RealmList<T>) {
        encoder.encodeSerializableValue(listSerializer, value.toList())
    }

    override fun deserialize(decoder: Decoder): RealmList<T> {
        return RealmList<T>().apply { addAll(decoder.decodeSerializableValue(listSerializer)) }
    }
}

/** Non-generic variants, because kapt stubs cannot reference a generic serializer from `@Serializable(with = ...)`. */
object RealmIntListSerializer : KSerializer<RealmList<Int>> by RealmListSerializer(Int.serializer())

object RealmCategoryListSerializer : KSerializer<RealmList<Category>> by RealmListSerializer(Category.serializer())

object RealmMaintenanceTypeListSerializer :
    KSerializer<RealmList<MaintenanceType>> by RealmListSerializer(MaintenanceType.serializer())

object RealmTeamDetailsListSerializer : KSerializer<RealmList<TeamDetails>> by RealmListSerializer(TeamDetails.serializer())
