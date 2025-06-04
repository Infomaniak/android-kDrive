package com.infomaniak.drive

import com.infomaniak.core.login.crossapp.BaseCrossAppLoginService
import com.infomaniak.drive.data.models.AppSettings
import kotlinx.coroutines.flow.Flow

class CrossAppLoginService : BaseCrossAppLoginService() {
    override val selectedUserIdFlow: Flow<Int> = AppSettings.currentUserIdFlow
}
