/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive.ui.login

import android.accounts.AccountManager
import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.ArrayMap
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.infomaniak.drive.BuildConfig
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.login.ApiToken
import com.infomaniak.lib.login.InfomaniakLogin
import kotlinx.android.synthetic.main.activity_migration.*
import kotlinx.android.synthetic.main.fragment_intro.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MigrationActivity : AppCompatActivity() {

    private val oldNotificationChannel = arrayListOf(
        "NOTIFICATION_CHANNEL_GENERAL",
        "NOTIFICATION_CHANNEL_DOWNLOAD",
        "NOTIFICATION_CHANNEL_UPLOAD",
        "NOTIFICATION_CHANNEL_MEDIA",
        "NOTIFICATION_CHANNEL_FILE_SYNC",
        "NOTIFICATION_CHANNEL_FILE_OBSERVER",
        "NOTIFICATION_CHANNEL_PUSH"
    )

    private lateinit var infomaniakLogin: InfomaniakLogin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_migration)

        infomaniakLogin = InfomaniakLogin(context = this, appUID = BuildConfig.APPLICATION_ID, clientID = BuildConfig.CLIENT_ID)

        header.title.setText(R.string.migrationTitle)
        header.description.setText(R.string.migrationDescription)

        loginInManuallyButton.setOnClickListener {
            clearOldUser()
            startActivity(Intent(this, LoginActivity::class.java).clearStack())
        }

        clearApplicationData()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
            for (oldChannel in oldNotificationChannel) {
                notificationManager.deleteNotificationChannel(oldChannel)
            }
        }

        authenticateOldUsers()
    }

    private fun authenticateOldUsers() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val newUserList = arrayListOf<User>()
            var textError = getString(R.string.migrationFailed)

            for (oldUser in this@MigrationActivity.getOldkDriveUser()) {
                var apiToken: ApiToken? = null
                var errorStatus: InfomaniakLogin.ErrorStatus? = null
                infomaniakLogin.getToken(
                    HttpClient.okHttpClientNoInterceptor,
                    username = oldUser.key,
                    password = oldUser.value,
                    onSuccess = { apiToken = it },
                    onError = { errorStatus = it })

                apiToken?.also {
                    when (val user = LoginActivity.authenticateUser(this@MigrationActivity, it)) {
                        is User -> newUserList.add(user)
                        is ApiResponse<*> -> {
                            textError = getString(user.translatedError)
                        }
                    }
                }

                errorStatus?.let {
                    val error = when (it) {
                        InfomaniakLogin.ErrorStatus.SERVER -> R.string.serverError
                        InfomaniakLogin.ErrorStatus.CONNECTION -> R.string.connectionError
                        else -> R.string.migrationFailed
                    }
                    textError = getString(error)
                }
            }

            withContext(Dispatchers.Main) {
                if (newUserList.isEmpty()) {
                    showError(textError)
                }
                startButton.isVisible = true
            }
        }
    }

    private fun showError(error: String) {
        showSnackbar(error)
        startButton.setOnClickListener {
            authenticateOldUsers()
        }
        startButton.setText(R.string.buttonRetry)
        loginInManuallyButton.isVisible = true
        progressMigration.isGone = true
        progressMigrationDescription.isGone = true
    }

    private fun showLoading() {
        startButton.isGone = true
        loginInManuallyButton.isGone = true
        progressMigration.isVisible = true
        progressMigrationDescription.isVisible = true
        startButton.setOnClickListener {
            AppSettings.migrated = true
            clearOldUser()
            startActivity(Intent(this, MainActivity::class.java).clearStack())
        }
    }

    private fun clearOldUser() {
        val accountManager = getSystemService(Service.ACCOUNT_SERVICE) as AccountManager
        for (currentAccount in accountManager.getAccountsByType(getString(R.string.ACCOUNT_TYPE))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                AccountManager.get(this).removeAccount(currentAccount, null, null, null)
            } else {
                AccountManager.get(this).removeAccount(currentAccount, null, null)
            }
        }
    }

    private fun clearApplicationData() {
        WorkManager.getInstance(applicationContext).cancelAllWork()

        val appDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dataDir
        } else {
            cacheDir.parent?.let { File(it) }
        }
        appDir?.let {
            if (it.exists()) {
                it.list()?.let { directoryList ->
                    for (fileName in directoryList) {
                        if (fileName != "lib") {
                            deleteFile(File(it, fileName))
                        }
                    }
                }
            }
        }
    }

    fun deleteFile(deleteFile: File): Boolean {
        var deletedAll = true
        if (deleteFile.isDirectory) {
            deleteFile.list()?.let {
                for (file in it) {
                    if (!file.contains("realm") && !file.contains("user_database") && !file.contains("workdb")) {
                        deletedAll = deleteFile(File(deleteFile, file)) && deletedAll
                    }
                }
            }
        } else {
            deletedAll = deleteFile.delete()
        }
        return deletedAll
    }

    companion object {

        fun Context.getOldkDriveUser(): ArrayMap<String, String> {
            val oldUserList: ArrayMap<String, String> = ArrayMap()
            val accountManager = getSystemService(Service.ACCOUNT_SERVICE) as AccountManager

            for (currentAccount in accountManager.getAccountsByType(getString(R.string.ACCOUNT_TYPE))) {
                val email = getUserEmail(currentAccount.name)
                val password = accountManager.getPassword(currentAccount)
                if (email != null && password != null) oldUserList[email] = password
            }

            return oldUserList
        }

        private fun getUserEmail(name: String): String? {
            val regex = Regex("([^@]+@[^@]+)@\\d+\\.connect\\.drive\\.infomaniak\\.com")
            return regex.find(name)?.groupValues?.get(1)
        }
    }
}
