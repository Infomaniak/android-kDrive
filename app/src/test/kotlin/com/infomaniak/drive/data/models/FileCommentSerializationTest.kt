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
package com.infomaniak.drive.data.models

import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.utils.SerializationUtils
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileCommentSerializationTest {

    @Test
    fun `decodes comment dates and defaults optional response fields`() {
        val comment = SerializationUtils.decodeFromString<FileComment>(
            """{"id":5,"user":{"id":7,"display_name":"Ada"},"body":"Hello","liked":false,"parent_id":3,
                "updated_at":1792675175,"created_at":1792675175,
                "likes_count":0,"is_resolved":false,"responses_count":0}"""
        )

        assertEquals(3, comment.parentId)
        assertEquals(Date(1_792_675_175_000L), comment.updatedAt)
        assertEquals(Date(1_792_675_175_000L), comment.createdAt)
        assertEquals(null, comment.likes)
        assertEquals(emptyList<FileComment>(), comment.responses)
    }

    @Test
    fun `decodes comment data inside an API response`() {
        val response = SerializationUtils.decodeFromString<ApiResponse<FileComment>>(
            """{"result":"success","data":{"id":5,"user":{"id":7,"display_name":"Ada"},"body":"Hello",
                "liked":false,"parent_id":3,"updated_at":1792675175,
                "created_at":1792675175,"likes_count":0,"is_resolved":false,"responses_count":0}}"""
        )

        assertEquals("Hello", response.data?.body)
        assertEquals(Date(1_792_675_175_000L), response.data?.createdAt)
    }
}
