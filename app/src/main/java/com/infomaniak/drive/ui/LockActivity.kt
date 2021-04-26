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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.requestCredentials
import kotlinx.android.synthetic.main.activity_lock.*

class LockActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_SECURITY = 1
        const val FACE_ID_LOG_TAG = "Face ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        requestCredentials {
            onCredentialsSuccessful()
        }
        unLock.setOnClickListener {
            requestCredentials {
                onCredentialsSuccessful()
            }
        }
    }

    private fun onCredentialsSuccessful() {
        Log.i(FACE_ID_LOG_TAG, "success")
        startMainActivity()
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SECURITY) {
            if (resultCode == Activity.RESULT_OK) {
                onCredentialsSuccessful()
            } else {
                Log.i(FACE_ID_LOG_TAG, "error")
            }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}