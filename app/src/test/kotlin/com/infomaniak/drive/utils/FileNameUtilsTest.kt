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
package com.infomaniak.drive.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class FileNameUtilsTest {

    @Test
    fun pathTraversalNames_areSanitized() {
        assertEquals(".._secret.txt", "../secret.txt".toSafeFileName())
        assertEquals("....__secret.txt", "....//secret.txt".toSafeFileName())
        assertEquals(".._.._secret.txt", """..\..\secret.txt""".toSafeFileName())
        assertEquals("_data_data_secret.txt", "/data/data/secret.txt".toSafeFileName())
    }

    @Test
    fun controlCharacters_areSanitized() {
        val safeName = "invoice.pdf\r\nInjected: true".toSafeFileName()

        assertFalse(safeName.contains('\r'))
        assertFalse(safeName.contains('\n'))
        assertFalse(safeName.contains(':'))
    }

    @Test
    fun invalidEmptyNames_useFallback() {
        assertEquals("file", "".toSafeFileName())
        assertEquals("file", "  ".toSafeFileName())
        assertEquals("file", ".".toSafeFileName())
        assertEquals("file", "..".toSafeFileName())
    }

    @Test
    fun unicodePathSeparators_areNormalizedBeforeSanitizing() {
        assertEquals("_data_secret.txt", "\uFF0Fdata\uFF0Fsecret.txt".toSafeFileName())
    }

    @Test
    fun longNames_areTruncatedAndKeepTheirExtension() {
        val safeName = "${"é".repeat(200)}.pdf".toSafeFileName()

        assertTrue(safeName.toByteArray(StandardCharsets.UTF_8).size <= 240)
        assertTrue(safeName.endsWith(".pdf"))
    }

    @Test
    fun validInternationalNames_areUnchanged() {
        assertEquals("résumé été 2026.pdf", "résumé été 2026.pdf".toSafeFileName())
    }

    @Test
    fun relativePaths_haveEachSegmentSanitized() {
        assertEquals("a/b/c.txt", "a/b/c.txt".toSafeRelativePath())
        assertEquals("data/data/secret", "/data/data/secret".toSafeRelativePath())
        assertEquals("a/file/file/b", "a/../../b".toSafeRelativePath())
        assertEquals("", "///".toSafeRelativePath())
    }

    @Test
    fun validResourcePath_keepsItsStructure() = withTemporaryDirectory { root ->
        val relativePath = "folder/123/message/456/attachment/789"

        assertEquals(File(root, relativePath).canonicalFile, root.resolveContainedPath(relativePath))
    }

    @Test
    fun traversingResourcePath_isRejected() = withTemporaryDirectory { root ->
        assertEquals(null, root.resolveContainedPath("folder/123/../../../../outside"))
        assertEquals(null, root.resolveContainedPath("../../etc/passwd"))
        assertEquals(null, root.resolveContainedPath("/data/data/outside"))
    }

    @Test
    fun sanitizedRelativePath_isAlwaysContained() = withTemporaryDirectory { root ->
        val hostilePath = "/../../etc/passwd".toSafeRelativePath()

        assertNotNull(root.resolveContainedPath(hostilePath))
    }

    @Test
    fun maliciousFileName_staysInsideExpectedDirectory() = withTemporaryDirectory { root ->
        val file = root.resolveContainedFileName("....//../../secret.txt")

        assertEquals(root.canonicalFile, file?.parentFile)
    }

    @Test
    fun ordinaryFileNames_areResolvedAsDirectChildren() = withTemporaryDirectory { root ->
        val file = root.resolveContainedFileName("report 2026.pdf")

        assertEquals(root.canonicalFile, file?.parentFile)
        assertEquals("report 2026.pdf", file?.name)
    }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("safe-file-name-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
