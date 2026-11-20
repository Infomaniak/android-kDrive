/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.ui.menu.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.applock.AppLockHelper.requestCredentials
import com.infomaniak.core.applock.AppLockManager
import com.infomaniak.core.applock.view.AppLockViewActivity
import com.infomaniak.core.common.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.ui.view.edgetoedge.EdgeToEdgeActivity
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.CachedStorageFile
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.databinding.ActivityLocalStorageBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.theme.DriveTheme
import com.infomaniak.drive.ui.theme.Typography
import com.infomaniak.drive.utils.StorageCategory
import dagger.hilt.android.AndroidEntryPoint
import com.infomaniak.core.common.R as RCommon

@AndroidEntryPoint
class LocalStorageActivity : EdgeToEdgeActivity() {

    private val viewModel: LocalStorageViewModel by viewModels()
    private var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLocalStorageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.enableEdgeToEdge()
        binding.toolbar.setNavigationOnClickListener { finish() }
        // The system can open this screen without first passing through MainActivity's app lock.
        locked = AppSettings.appSecurityLock && (intent.action == Intent.ACTION_DEFAULT || callingPackage != null)
        if (!locked) scheduleAppLock()

        binding.storageContent.setContent {
            DriveTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LocalStorageScreen(
                    state = state,
                    locked = locked,
                    onUnlock = ::unlock,
                    onConfirmCleanup = ::confirmCleanup,
                    onRetry = viewModel::refresh,
                    onClearImages = viewModel::clearImages,
                    onClearFiles = { files -> viewModel.clearFiles(files) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!locked) viewModel.refresh()
    }

    private fun unlock() {
        requestCredentials {
            AppLockManager.unlock()
            locked = false
            scheduleAppLock()
            viewModel.refresh()
        }
    }

    private fun scheduleAppLock() {
        AppLockManager.scheduleLockIfNeeded(this, AppLockViewActivity::class, isAppLockEnabled = { AppSettings.appSecurityLock })
    }

    private fun confirmCleanup(title: String, cleanup: () -> Unit) {
        MaterialAlertDialogBuilder(this, R.style.DialogStyle)
            .setTitle(R.string.localStorageClearTitle)
            .setMessage(getString(R.string.localStorageClearConfirmation, title))
            .setNegativeButton(RCommon.string.buttonCancel, null)
            .setPositiveButton(R.string.buttonClear) { _, _ -> cleanup() }
            .show()
    }
}

@Composable
private fun LocalStorageScreen(
    state: LocalStorageViewModel.State,
    locked: Boolean,
    onUnlock: () -> Unit,
    onConfirmCleanup: (String, () -> Unit) -> Unit,
    onRetry: () -> Unit,
    onClearImages: () -> Unit,
    onClearFiles: (List<CachedStorageFile>) -> Unit,
) {
    val usage = state.usage
    if (locked || usage == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                locked -> TextButton(onClick = onUnlock) { Text(stringResource(R.string.localStorageUnlock)) }
                state.busy -> CircularProgressIndicator()
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.localStorageReadError))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.buttonRetry)) }
                }
            }
        }
        return
    }

    val margin = dimensionResource(R.dimen.marginStandard)
    val sectionGap = dimensionResource(R.dimen.marginStandardMedium)
    val radius = dimensionResource(R.dimen.cardViewRadius)
    val cardBackground = colorResource(R.color.backgroundCardview)
    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = PaddingValues(margin),
    ) {
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.cleanupFailed) item {
            Text(stringResource(R.string.localStorageCleanupError), color = MaterialTheme.colorScheme.error)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(radius),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
            ) {
                StorageRow(stringResource(R.string.localStorageTotal), usage.totalBytes)
            }
        }
        item { StorageHeading(stringResource(R.string.localStorageData), Modifier.padding(top = margin, bottom = sectionGap)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(radius),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
            ) {
                StorageCategory.entries.forEachIndexed { index, category ->
                    if (index > 0) StorageDivider()
                    val bytes = usage.sizes[category] ?: 0L
                    val title = stringResource(category.title)
                    val cleanup: (() -> Unit)? = when (category) {
                        StorageCategory.ImageCache -> onClearImages
                        StorageCategory.CachedDownloads, StorageCategory.CachedPreviews -> {
                            { onClearFiles(usage.files.filter { it.category == category }) }
                        }
                        else -> null
                    }
                    val onClear: (() -> Unit)? = if (state.busy || bytes == 0L || cleanup == null) null else {
                        { onConfirmCleanup(title, cleanup) }
                    }
                    StorageRow(title, bytes, onClear)
                }
            }
        }
        item { StorageHeading(stringResource(R.string.localStorageCachedFiles), Modifier.padding(top = margin, bottom = sectionGap)) }
        if (usage.files.isEmpty()) item {
            Text(stringResource(R.string.localStorageNoCachedFiles), color = colorResource(R.color.secondaryText))
        }
        itemsIndexed(usage.files, key = { _, file -> file.file.path.toString() }) { index, file ->
            val title = file.name
                ?: file.id?.let { stringResource(R.string.localStorageFileId, it) }
                ?: file.file.path.fileName.toString()
            val top = if (index == 0) radius else 0.dp
            val bottom = if (index == usage.files.lastIndex) radius else 0.dp
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                    .background(cardBackground),
            ) {
                if (index > 0) StorageDivider()
                StorageRow(
                    name = title,
                    bytes = file.file.bytes,
                    onClear = if (state.busy) null else {
                        { onConfirmCleanup(title) { onClearFiles(listOf(file)) } }
                    },
                )
            }
        }
    }
}

@Composable
private fun StorageHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = settingsTitleStyle, color = colorResource(R.color.title))
}

@Composable
private fun StorageRow(name: String, bytes: Long, onClear: (() -> Unit)? = null) {
    val action = if (onClear == null) Modifier else {
        Modifier.clickable(onClickLabel = stringResource(R.string.buttonClear), onClick = onClear)
    }
    Row(
        modifier = Modifier.fillMaxWidth().then(action)
            .heightIn(min = dimensionResource(R.dimen.buttonHeight))
            .padding(horizontal = dimensionResource(R.dimen.marginStandard), vertical = dimensionResource(R.dimen.marginStandardSmall)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.marginStandardSmall)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = settingsTitleStyle,
            color = colorResource(R.color.title),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
        Text(
            LocalContext.current.formatShortFileSize(bytes),
            color = colorResource(R.color.primaryText),
            fontFamily = FontFamily(Font(R.font.suisseintl_regular)),
        )
        if (onClear != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colorResource(R.color.icon_toolbar_button),
            )
        }
    }
}

@Composable
private fun StorageDivider() {
    HorizontalDivider(color = colorResource(R.color.divider))
}

private val settingsTitleStyle = Typography.subtitle2.copy(fontFamily = FontFamily(Font(R.font.suisseintl_medium)))

private val StorageCategory.title: Int
    get() = when (this) {
        StorageCategory.OfflineFiles -> R.string.localStorageOfflineFiles
        StorageCategory.Imports -> R.string.localStorageImports
        StorageCategory.DocumentProvider -> R.string.localStorageDocumentProvider
        StorageCategory.CachedDownloads -> R.string.localStorageCachedDownloads
        StorageCategory.CachedPreviews -> R.string.localStorageCachedPreviews
        StorageCategory.ImageCache -> R.string.localStorageImageCache
        StorageCategory.OtherCache -> R.string.localStorageOtherCache
        StorageCategory.OtherData -> R.string.localStorageOtherData
    }
