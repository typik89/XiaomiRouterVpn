package ru.typik.mi.router.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.typik.mi.router.android.client.AdminClient
import ru.typik.mi.router.android.client.model.Vpn
import ru.typik.mi.router.android.data.ConnectionSettings
import ru.typik.mi.router.android.data.SettingsStorage

private const val DEFAULT_BASE_URL = "192.168.31.1"
private const val DEFAULT_USERNAME = "admin"
private val IP_FORMAT_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
private const val LOG_TAG = "XiaomiRouterVpn"

data class SettingsUiState(
    val baseUrl: String = DEFAULT_BASE_URL,
    val username: String = DEFAULT_USERNAME,
    val password: String = "",
    val baseUrlError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val showRestoreButton: Boolean = false
)

data class VpnUiState(
    val loading: Boolean = true,
    val vpnList: List<Vpn> = emptyList(),
    val globalStatus: Boolean? = null,
    val statusError: String? = null,
    val screenError: String? = null
)

class AppViewModel(
    private val storage: SettingsStorage
) : ViewModel() {
    private var adminClient: AdminClient? = null
    private var adminClientConfigKey: String? = null

    private val _settingsState = MutableStateFlow(buildInitialSettingsState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _vpnState = MutableStateFlow(VpnUiState())
    val vpnState: StateFlow<VpnUiState> = _vpnState.asStateFlow()

    fun onBaseUrlChange(value: String) {
        _settingsState.value = _settingsState.value.copy(baseUrl = value, baseUrlError = null)
        refreshRestoreVisibility()
    }

    fun onUsernameChange(value: String) {
        _settingsState.value = _settingsState.value.copy(username = value, usernameError = null)
        refreshRestoreVisibility()
    }

    fun onPasswordChange(value: String) {
        _settingsState.value = _settingsState.value.copy(password = value, passwordError = null)
        refreshRestoreVisibility()
    }

    fun restoreSettings() {
        val saved = storage.loadOrNull() ?: return
        _settingsState.value = _settingsState.value.copy(
            baseUrl = saved.baseUrl,
            username = saved.username,
            password = saved.password,
            baseUrlError = null,
            usernameError = null,
            passwordError = null,
            showRestoreButton = false
        )
    }

    fun saveSettings(onSuccess: () -> Unit) {
        val base = _settingsState.value.baseUrl.trim()
        val user = _settingsState.value.username.trim()
        val pass = _settingsState.value.password

        val baseError = when {
            base.isEmpty() -> "Поле не должно быть пустым"
            !base.matches(IP_FORMAT_REGEX) -> "Некорректный формат IP (ожидается N.N.N.N)"
            else -> null
        }
        val userError = if (user.isEmpty()) "Поле не должно быть пустым" else null
        val passError = if (pass.isEmpty()) "Поле не должно быть пустым" else null

        _settingsState.value = _settingsState.value.copy(
            baseUrlError = baseError,
            usernameError = userError,
            passwordError = passError
        )

        if (baseError != null || userError != null || passError != null) {
            return
        }

        storage.save(ConnectionSettings(base, user, pass))
        resetAdminClient()
        refreshRestoreVisibility()
        onSuccess()
    }

    fun refreshVpn() {
        viewModelScope.launch(Dispatchers.IO) {
            _vpnState.value = _vpnState.value.copy(loading = true, screenError = null)
            val saved = storage.loadOrNull()
            if (saved == null) {
                _vpnState.value = VpnUiState(
                    loading = false,
                    screenError = "Настройки подключения не заполнены"
                )
                return@launch
            }

            val previous = _vpnState.value
            runCatching {
                val client = getOrCreateAdminClient(saved)
                VpnUiState(
                    loading = false,
                    vpnList = client.getVpnList(),
                    globalStatus = client.getVpnStatus(),
                    statusError = null
                )
            }.onSuccess { state ->
                _vpnState.value = state
            }.onFailure { error ->
                Log.e(LOG_TAG, "refreshVpn failed", error)
                invalidateAdminClientOnFailure(saved)
                _vpnState.value = VpnUiState(
                    loading = false,
                    vpnList = previous.vpnList,
                    globalStatus = previous.globalStatus,
                    statusError = previous.statusError,
                    screenError = error.message ?: error.toString()
                )
            }
        }
    }

    fun changeVpnStatus(vpn: Vpn, connect: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _vpnState.value = _vpnState.value.copy(loading = true)
            val saved = storage.loadOrNull()
            if (saved == null) {
                _vpnState.value = VpnUiState(
                    loading = false,
                    screenError = "Настройки подключения не заполнены"
                )
                return@launch
            }

            runCatching {
                val client = getOrCreateAdminClient(saved)
                client.changeStatusVpn(vpn, connect)
            }.onFailure { error ->
                Log.e(LOG_TAG, "changeVpnStatus failed", error)
                invalidateAdminClientOnFailure(saved)
                _vpnState.value = VpnUiState(
                    loading = false,
                    screenError = error.message ?: error.toString()
                )
                return@launch
            }

            _vpnState.value = _vpnState.value.copy(globalStatus = connect, statusError = null)
            refreshVpn()
        }
    }

    private fun getOrCreateAdminClient(saved: ConnectionSettings): AdminClient {
        val key = "${saved.baseUrl.trim()}|${saved.username}|${saved.password}"
        val cached = adminClient
        if (cached != null && adminClientConfigKey == key) {
            return cached
        }

        val client = AdminClient(normalizeBaseUrl(saved.baseUrl), saved.username, saved.password)
        client.init()
        adminClient = client
        adminClientConfigKey = key
        return client
    }

    private fun resetAdminClient() {
        adminClient = null
        adminClientConfigKey = null
    }

    private fun invalidateAdminClientOnFailure(saved: ConnectionSettings) {
        val key = "${saved.baseUrl.trim()}|${saved.username}|${saved.password}"
        if (adminClientConfigKey == key) {
            resetAdminClient()
        }
    }

    fun hasSavedSettings(): Boolean = storage.hasSavedSettings()

    private fun buildInitialSettingsState(): SettingsUiState {
        val saved = storage.loadOrNull() ?: return SettingsUiState()
        return SettingsUiState(
            baseUrl = saved.baseUrl,
            username = saved.username,
            password = saved.password,
            showRestoreButton = false
        )
    }

    private fun refreshRestoreVisibility() {
        val saved = storage.loadOrNull()
        val visible = if (saved == null) {
            false
        } else {
            _settingsState.value.baseUrl.trim() != saved.baseUrl ||
                    _settingsState.value.username.trim() != saved.username ||
                    _settingsState.value.password != saved.password
        }
        _settingsState.value = _settingsState.value.copy(showRestoreButton = visible)
    }

    private fun normalizeBaseUrl(raw: String): String {
        return if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            "http://$raw"
        }
    }
}
