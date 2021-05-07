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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import coil.ImageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.UISettings
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.sync.UploadProgressReceiver
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.checkSyncPermissionsResult
import com.infomaniak.drive.utils.SyncUtils.launchAllUpload
import com.infomaniak.drive.utils.SyncUtils.startContentObserverService
import com.infomaniak.drive.utils.Utils.getRootName
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : BaseActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var uploadProgressReceiver: UploadProgressReceiver
    private var lastCloseApp = Date()
    private var updateAvailableShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        uploadProgressReceiver = UploadProgressReceiver(mainViewModel)
        FileController.switchDriveDB(UserDrive())

        val navController = findNavController(R.id.hostFragment)
        bottomNavigation.setupWithNavController(navController)
        bottomNavigation.itemIconTintList = ContextCompat.getColorStateList(this, R.color.item_icon_tint_bottom)
        bottomNavigation.selectedItemId = UISettings(this).bottomNavigationSelectedItem

        intent?.getIntExtra(INTENT_SHOW_PROGRESS, 0)?.let { folderId ->
            if (folderId > 0) {
                bottomNavigation.selectedItemId = R.id.fileListFragment
                mainViewModel.intentShowProgressByFolderId.value = folderId
            }
        }

        LiveDataNetworkStatus(this).observe(this, { isAvailable ->
            Log.d("Internet availability", if (isAvailable) "Available" else "Unavailable")
            mainViewModel.isInternetAvailable.value = isAvailable
            if (isAvailable) {
                lifecycleScope.launch {
                    AccountUtils.updateCurrentUserAndDrives(this@MainActivity)
                }
            }
        })

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val visibility = when (destination.id) {
                R.id.addFileBottomSheetDialog,
                R.id.favoritesFragment,
                R.id.fileInfoActionsBottomSheetDialog,
                R.id.fileListFragment,
                R.id.homeFragment,
                R.id.menuFragment -> VISIBLE
                else -> GONE
            }
            mainFab.visibility = visibility
            bottomNavigation.visibility = visibility
            bottomNavigationBackgroundView.visibility = when (destination.id) {
                R.id.homeFragment,
                R.id.menuFragment -> VISIBLE
                else -> GONE
            }
            bottomNavigationBackgroundView2.visibility = when (destination.id) {
                R.id.favoritesFragment,
                R.id.fileListFragment -> VISIBLE
                else -> GONE
            }

            when (destination.id) {
                R.id.favoritesFragment,
                R.id.homeFragment,
                R.id.menuFragment -> {
                    // Defining default root folder
                    mainViewModel.currentFolder.value = AccountUtils.getCurrentDrive()?.convertToFile(getRootName(this))
                }
            }

            when (destination.id) {
                R.id.fileDetailsFragment, R.id.fileShareLinkSettingsFragment, R.id.fileListFragment -> {
                    setColorStatusBar(destination.id == R.id.fileShareLinkSettingsFragment)
                    setColorNavigationBar(true)
                }
                R.id.downloadProgressDialog, R.id.previewSliderFragment -> Unit
                else -> {
                    setColorStatusBar()
                    setColorNavigationBar()
                }
            }
        }

        mainFab.setOnClickListener { navController.navigate(R.id.addFileBottomSheetDialog) }
        mainViewModel.currentFolder.observe(this) { file ->
            mainFab.isEnabled = file?.rights?.newFile == true
        }

        launchAllUpload()

        if (!BuildConfig.BETA)
            if (AppSettings.appLaunches == 20 || (AppSettings.appLaunches != 0 && AppSettings.appLaunches % 100 == 0)) launchInAppReview()

        if (!UISettings(this).updateLater || AppSettings.appLaunches % 10 == 0) {
            // Will never be successful on emulator (update check is not functional on AVD)
            AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                val updateIsAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

                if (!updateAvailableShow && updateIsAvailable) {
                    findNavController(R.id.hostFragment).navigate(R.id.updateAvailableBottomSheetDialog)
                    updateAvailableShow = true
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.syncOfflineFiles(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()

        if (isKeyguardSecure() && AppSettings.appSecurityLock) {
            val lastCloseAppWithTolerance = Date(lastCloseApp.time + SECURITY_APP_TOLERANCE)
            val now = Date()
            if (now.after(lastCloseAppWithTolerance)) {
                startActivity(Intent(this, LockActivity::class.java))
            }
        }

        AppSettings.appLaunches++
        if (!AccountUtils.isEnableAppSync() && AppSettings.appLaunches == 1) {
            val id = if (AppSettings.migrated) R.id.syncAfterMigrationBottomSheetDialog else R.id.syncConfigureBottomSheetDialog
            findNavController(R.id.hostFragment).navigate(id)
        }

        setBottomNavigationUserAvatar(this)
        startContentObserverService()

        LocalBroadcastManager.getInstance(this).registerReceiver(uploadProgressReceiver, IntentFilter(UploadProgressReceiver.TAG))
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadProgressReceiver)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        UISettings(this).bottomNavigationSelectedItem = bottomNavigation.selectedItemId
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSyncPermissionsResult(requestCode, grantResults)) {
            launchAllUpload()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setBottomNavigationUserAvatar(context: Context) {
        lifecycleScope.launch(Dispatchers.IO) {
            val menuItemView = (bottomNavigation.getChildAt(0) as BottomNavigationMenuView)[4] as NavigationBarItemView
            val imageLoader = ImageLoader.Builder(this@MainActivity).build()
            val request = ImageRequest.Builder(context)
                .data(AccountUtils.currentUser?.avatar)
                .crossfade(true)
                .transformations(CircleCropTransformation())
                .fallback(R.drawable.ic_account)
                .listener { _, _ ->
                    menuItemView.setIconTintList(null)
                }.build()
            val userAvatar = imageLoader.execute(request).drawable

            userAvatar?.let {
                val selectedAvatar = generateSelectedAvatar(userAvatar)
                val stateListDrawable = StateListDrawable()
                stateListDrawable.addState(intArrayOf(android.R.attr.state_checked), BitmapDrawable(resources, selectedAvatar))
                stateListDrawable.addState(intArrayOf(), userAvatar)

                withContext(Dispatchers.Main) {
                    menuItemView.setIcon(stateListDrawable)
                    bottomNavigation.menu.findItem(R.id.menuFragment).icon = stateListDrawable
                }
            }
        }
    }

    // TODO Better implementation needed
    private fun generateSelectedAvatar(userAvatar: Drawable): Bitmap {
        val bitmap = userAvatar.toBitmap(100, 100)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        paint.color = ContextCompat.getColor(this@MainActivity, R.color.primary)
        paint.strokeWidth = 8F
        paint.style = Paint.Style.STROKE
        paint.isAntiAlias = true
        paint.isDither = true

        canvas.drawCircle(50F, 50F, 46F, paint)
        return bitmap
    }

    private fun launchInAppReview() {
        ReviewManagerFactory.create(this).apply {
            val requestReviewFlow = requestReviewFlow()
            requestReviewFlow.addOnCompleteListener { request ->
                if (request.isSuccessful) launchReviewFlow(this@MainActivity, request.result)
            }
        }
    }

    companion object {
        private const val SECURITY_APP_TOLERANCE = 1 * 60 * 1000 // 1min (ms)
        const val INTENT_SHOW_PROGRESS = "intent_folder_id_progress"
    }
}
