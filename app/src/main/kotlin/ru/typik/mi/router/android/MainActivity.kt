package ru.typik.mi.router.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.typik.mi.router.android.client.model.Vpn
import ru.typik.mi.router.android.data.SettingsStorage
import ru.typik.mi.router.android.ui.AppViewModel
import ru.typik.mi.router.android.ui.AppViewModelFactory
import ru.typik.mi.router.android.ui.SettingsUiState
import ru.typik.mi.router.android.ui.VpnUiState

class MainActivity : ComponentActivity() {
    companion object {
        private const val LOG_TAG = "XiaomiRouterVpn"
    }

    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory(SettingsStorage(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(LOG_TAG, "Uncaught exception in thread ${thread.name}", error)
            previousHandler?.uncaughtException(thread, error)
        }
        setContent {
            MaterialTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val startDestination = if (viewModel.hasSavedSettings()) {
        Screen.Vpn.route
    } else {
        Screen.Settings.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Settings.route) {
            val state by viewModel.settingsState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onBaseUrlChange = viewModel::onBaseUrlChange,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onRestore = viewModel::restoreSettings,
                onSave = {
                    viewModel.saveSettings {
                        navController.navigate(Screen.Vpn.route) {
                            popUpTo(Screen.Settings.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Vpn.route) {
            val state by viewModel.vpnState.collectAsStateWithLifecycle()
            VpnScreen(
                navController = navController,
                state = state,
                onRefresh = viewModel::refreshVpn,
                onChangeStatus = viewModel::changeVpnStatus
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsScreen(
    state: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRestore: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Настройки подключения") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("IP админки") },
                supportingText = state.baseUrlError?.let { { Text(it) } },
                isError = state.baseUrlError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text("Пользователь") },
                supportingText = state.usernameError?.let { { Text(it) } },
                isError = state.usernameError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = state.passwordError?.let { { Text(it) } },
                isError = state.passwordError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) {
                    Text("Сохранить")
                }
                if (state.showRestoreButton) {
                    Button(onClick = onRestore) {
                        Text("Восстановить")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VpnScreen(
    navController: NavHostController,
    state: VpnUiState,
    onRefresh: () -> Unit,
    onChangeStatus: (Vpn, Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = "Обновить"
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Настройки"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (state.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(12.dp))
            }

            state.screenError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.vpnList, key = { it.id }) { vpn ->
                    VpnRow(
                        vpn = vpn,
                        state = state,
                        onChangeStatus = onChangeStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun VpnRow(
    vpn: Vpn,
    state: VpnUiState,
    onChangeStatus: (Vpn, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(vpn.title, modifier = Modifier.weight(1f))
        if (state.statusError != null) {
            Text(state.statusError, color = MaterialTheme.colorScheme.error)
        } else {
            val isConnected = state.globalStatus == true
            Switch(
                checked = isConnected,
                onCheckedChange = { checked -> onChangeStatus(vpn, checked) },
                enabled = !state.loading
            )
        }
    }
}

private sealed class Screen(val route: String) {
    data object Settings : Screen("settings")
    data object Vpn : Screen("vpn")
}
