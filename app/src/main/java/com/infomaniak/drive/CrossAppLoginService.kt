package com.infomaniak.drive

import com.infomaniak.core.login.crossapp.BaseCrossAppLoginService
import com.infomaniak.drive.data.models.AppSettings

class CrossAppLoginService : BaseCrossAppLoginService(AppSettings.currentUserIdFlow2)
