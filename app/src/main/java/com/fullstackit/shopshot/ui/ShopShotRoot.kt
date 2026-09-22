package com.fullstackit.shopshot.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.net.URLDecoder
import java.net.URLEncoder

private const val ROUTE_CAMERA = "camera"
private const val ROUTE_FOLDERS = "folders"
private const val ROUTE_FOLDER_DETAIL = "folder/{name}"

@Composable
fun ShopShotRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbars = remember { SnackbarHostState() }

    // MediaStore refuses to touch another app's photos without a one-tap system dialog;
    // this launcher runs that dialog and hands the answer back to the ViewModel.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Toast -> snackbars.showSnackbar(event.message)
                is UiEvent.Consent -> consentLauncher.launch(
                    IntentSenderRequest.Builder(event.intentSender).build()
                )
            }
        }
    }

    var hasCamera by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    // Reading photos other apps put in the shop folders is a bonus, never a blocker, so this
    // is asked for once in the background and the app works fine if it is refused.
    val readPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refresh() }

    LaunchedEffect(Unit) {
        if (!hasCamera) cameraPermission.launch(Manifest.permission.CAMERA)
        val readPerm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (context.checkSelfPermission(readPerm) != PackageManager.PERMISSION_GRANTED) {
            readPermission.launch(readPerm)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!hasCamera) {
            CameraPermissionPrompt(
                modifier = Modifier.fillMaxSize().padding(padding),
                onGrant = { cameraPermission.launch(Manifest.permission.CAMERA) },
            )
            return@Scaffold
        }

        NavHost(
            navController = navController,
            startDestination = ROUTE_CAMERA,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ROUTE_CAMERA) {
                CameraScreen(
                    state = state,
                    vm = vm,
                    onOpenFolders = { navController.navigate(ROUTE_FOLDERS) },
                    onOpenFolder = { name -> navController.navigate(folderRoute(name)) },
                )
            }
            composable(ROUTE_FOLDERS) {
                FoldersScreen(
                    state = state,
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { name -> navController.navigate(folderRoute(name)) },
                )
            }
            composable(ROUTE_FOLDER_DETAIL) { entry ->
                val name = entry.arguments?.getString("name").orEmpty().let {
                    runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                }
                FolderDetailScreen(
                    folderName = name,
                    state = state,
                    vm = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun folderRoute(name: String): String =
    "folder/" + URLEncoder.encode(name, "UTF-8")

@Composable
private fun CameraPermissionPrompt(modifier: Modifier = Modifier, onGrant: () -> Unit) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ShopShot needs the camera",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "That is the whole app: take a photo, and it lands straight in the " +
                    "listing folder you picked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) { Text("Allow camera") }
        }
    }
}
