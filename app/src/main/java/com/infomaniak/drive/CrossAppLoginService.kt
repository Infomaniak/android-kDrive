package com.infomaniak.drive

import com.infomaniak.core.crosslogin.back.BaseCrossAppLoginService
import com.infomaniak.drive.data.models.AppSettings

class CrossAppLoginService : BaseCrossAppLoginService(AppSettings.currentUserIdFlow)
