/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.car.app.connection.CarConnection
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import kotlin.math.max
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.MainActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.chat.fragment.ConversationsListFragmentDirections
import org.linphone.utils.PasswordDialogModel
import org.linphone.ui.main.viewmodel.MainViewModel
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.ui.main.splash.MyTelIntroOverlay
import org.linphone.ui.welcome.WelcomeActivity
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import androidx.core.content.edit
import org.linphone.ui.sso.SingleSignOnActivity

@UiThread
class MainActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Main Activity]"

        private const val DEFAULT_FRAGMENT_KEY = "default_fragment"
        private const val CONTACTS_FRAGMENT_ID = 1
        private const val HISTORY_FRAGMENT_ID = 2
        private const val CHAT_FRAGMENT_ID = 3
        private const val MEETINGS_FRAGMENT_ID = 4

        private const val REDIRECT_ROLE_REQUEST_CODE = 101

        const val ARGUMENTS_CHAT = "Chat"
        const val ARGUMENTS_CONVERSATION_ID = "ConversationId"

        // The branded intro overlay plays once per process, only for ordinary
        // cold launcher openings (GPT-6.1-sol leader decision #2, 2026-11-11)
        private var introShownThisProcess = false

        // Bounded readiness wait before the retry screen is shown
        private const val CORE_READY_TIMEOUT_MS = 30_000L
    }

    private lateinit var binding: MainActivityBinding

    private lateinit var viewModel: MainViewModel

    private lateinit var sharedViewModel: SharedMainViewModel

    private var currentlyDisplayedAuthDialog: Dialog? = null

    private var navigatedToDefaultFragment = false

    private var introOverlay: MyTelIntroOverlay? = null

    // Released only once the core is up and the intro overlay decision has
    // been applied (or an alternate destination was chosen)
    @Volatile
    private var keepSystemSplashVisible = true

    // Non-blocking core readiness wait (leader correction 2026-11-11):
    // the UI thread must stay responsive for the intro animation and skipping
    private val coreReadyHandler = Handler(Looper.getMainLooper())

    // onPostCreate may run before the core is ready; the saved state can be
    // null on a fresh launch, so the deferral needs its own flag
    private var deferredOnPostCreate = false

    private var deferredOnPostCreateSavedState: Bundle? = null

    // Leader correction 2026-11-11: keep every intent that arrives while the
    // core is not ready; a single slot silently overwrote earlier arrivals
    private val pendingIntentsWhileCoreNotReady = mutableListOf<Intent>()

    // Leader correction 2026-11-11 (round 2): an actionable intent (call,
    // share, dial, shortcut, deep link) must interrupt the running intro and
    // suppress any pending intro work — including the first-run destination
    // start, so Welcome/Assistant can never cover an incoming destination
    private var actionableIntentInterruption = false

    private val destinationListener = object : NavController.OnDestinationChangedListener {
        override fun onDestinationChanged(
            controller: NavController,
            destination: NavDestination,
            arguments: Bundle?
        ) {
            Log.i("$TAG Latest visited fragment was restored")
            navigatedToDefaultFragment = true
            controller.removeOnDestinationChangedListener(this)
        }
    }

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG POST_NOTIFICATIONS permission has been granted")
            viewModel.updateMissingPermissionAlert()
        } else {
            Log.w("$TAG POST_NOTIFICATIONS permission has been denied!")
        }
    }

    private val fullScreenIntentPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG USE_FULL_SCREEN_INTENT permission has been granted")
            viewModel.updateMissingPermissionAlert()
        } else {
            Log.w("$TAG USE_FULL_SCREEN_INTENT permission has been denied!")
        }
    }

    private val accessLocalNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG ACCESS_LOCAL_NETWORK permission has been granted")
            viewModel.updateMissingPermissionAlert()
        } else {
            Log.w("$TAG ACCESS_LOCAL_NETWORK permission has been denied!")
        }
    }

    private val requestReadPhonePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        for (permission in permissions) {
            val isGranted = permission.value
            if (isGranted) {
                Log.i("$TAG [${permission.key}] permission has been granted")
            } else {
                allGranted = false
                Log.w("$TAG [${permission.key}] permission has been denied, toggling OFF Call Redirection setting")
                corePreferences.useCallRedirectionService = false
            }
        }

        if (allGranted) {
            Log.i("$TAG Permissions required for Call Redirection have been granted, requesting redirection role")
            requestCallRedirectionRole()
        }
    }

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be done before the setContentView
        val splashScreen = installSplashScreen()
        // Keep the system splash visible until the core is up AND the intro
        // decision has been made, so nothing (fragments included) inflates
        // before the core exists and the first frame matches the splash
        splashScreen.setKeepOnScreenCondition { keepSystemSplashVisible }

        val sw600dpLand = resources.configuration.smallestScreenWidthDp >= 600 &&
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (sw600dpLand) {
            // Do not force white icons in status bar as it will use the app's background color depending on device light/dark theme
            Log.i("$TAG Device is in sw600dp-land configuration")
            enableEdgeToEdge()
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                    true // Force dark mode to always have white icons in status bar, black over orange isn't looking great
                }
            )
        }

        super.onCreate(savedInstanceState)

        // Leader correction 2026-11-11: never block the UI thread waiting for
        // the core — keep the system splash on screen and poll from the main
        // looper instead; content view, fragments and models are only created
        // once the core is ready
        if (coreContext.isReady()) {
            onCoreReady()
        } else {
            Log.i("$TAG Core not ready yet, deferring initialization without blocking the UI thread")
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val pollUntilCoreReady: Runnable = object : Runnable {
                override fun run() {
                    if (isDestroyed || isFinishing) {
                        Log.w("$TAG Activity destroyed while waiting for the core, giving up")
                        return
                    }
                    if (coreContext.isReady()) {
                        onCoreReady()
                    } else if (android.os.SystemClock.elapsedRealtime() - startedAt > coreReadyTimeoutMs()) {
                        // Leader correction 2026-11-11: bounded readiness —
                        // never keep the system splash up indefinitely
                        onCoreReadyTimedOut()
                    } else {
                        coreReadyHandler.postDelayed(this, 50)
                    }
                }
            }
            coreReadyHandler.post(pollUntilCoreReady)
        }
    }

    /**
     * Production always uses the fixed [CORE_READY_TIMEOUT_MS] deadline. The
     * settings-global override exists ONLY in debug builds for the
     * deterministic timeout test (leader correction: release must ignore it).
     */
    private fun coreReadyTimeoutMs(): Long {
        if (!BuildConfig.DEBUG) return CORE_READY_TIMEOUT_MS
        val effective = try {
            android.provider.Settings.Global.getLong(
                contentResolver,
                "mytel_core_ready_timeout",
                CORE_READY_TIMEOUT_MS
            )
        } catch (e: Exception) {
            CORE_READY_TIMEOUT_MS
        }
        Log.i("$TAG Core-ready timeout (debug override): [$effective] ms")
        return effective
    }

    /**
     * Bounded readiness failure (leader correction 2026-11-11): release the
     * held splash and offer a retry instead of waiting forever.
     */
    private fun onCoreReadyTimedOut() {
        Log.e("$TAG Core was not ready within [$CORE_READY_TIMEOUT_MS] ms, giving up")
        keepSystemSplashVisible = false
        val retry = android.widget.TextView(this).apply {
            text = getString(R.string.mytel_startup_failed_retry)
            setTextColor(0xFFE8F1FC.toInt())
            setBackgroundColor(0xFF001622.toInt())
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                Log.i("$TAG Retry requested after startup timeout, restarting process")
                val restart = packageManager.getLaunchIntentForPackage(packageName)
                restart?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(restart)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        setContentView(retry)
    }

    private fun onCoreReady() {
        Log.i("$TAG Core is ready, creating content view and models")
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        // Will give the device's status bar background color
        ViewCompat.setOnApplyWindowInsetsListener(binding.notificationsArea) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(0, insets.top, 0, 0)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainNavContainer) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(insets.left, 0, insets.right, max(insets.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerMenuContent) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        viewModel = run {
            ViewModelProvider(this)[MainViewModel::class.java]
        }
        binding.viewModel = viewModel

        sharedViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        viewModel.goBackToCallEvent.observe(this) {
            it.consume {
                coreContext.showCallActivity()
            }
        }

        viewModel.openDrawerEvent.observe(this) {
            it.consume {
                openDrawerMenu()
            }
        }

        viewModel.askPostNotificationsPermissionEvent.observe(this) {
            it.consume {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    Log.w("$TAG Asking for POST_NOTIFICATIONS permission")
                    postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Log.e("$TAG Permission request for POST_NOTIFICATIONS will be automatically denied, go to android app settings instead")
                    goToAndroidPermissionSettings()
                }
            }
        }

        viewModel.askFullScreenIntentPermissionEvent.observe(this) {
            it.consume {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.USE_FULL_SCREEN_INTENT)) {
                    Log.w("$TAG Asking for USE_FULL_SCREEN_INTENT permission")
                    fullScreenIntentPermissionLauncher.launch(Manifest.permission.USE_FULL_SCREEN_INTENT)
                } else {
                    Log.e("$TAG Permission request for USE_FULL_SCREEN_INTENT will be automatically denied, go to manage app full screen intent android settings instead")
                    Compatibility.requestFullScreenIntentPermission(this)
                }
            }
        }

        viewModel.askAccessLocalNetworkPermissionEvent.observe(this) {
            it.consume {
                Log.w("$TAG Asking for ACCESS_LOCAL_NETWORK permission")
                accessLocalNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }

        viewModel.showNewAccountToastEvent.observe(this) {
            it.consume {
                val message = getString(R.string.new_account_configured_toast)
                showGreenToast(message, R.drawable.user_circle)
            }
        }

        viewModel.startLoadingContactsEvent.observe(this) {
            it.consume {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    loadContacts()
                }
            }
        }

        viewModel.lastAccountRemovedEvent.observe(this) {
            it.consume {
                startActivity(Intent(this, AssistantActivity::class.java))
            }
        }

        viewModel.clearFilesOrTextPendingSharingEvent.observe(this) {
            it.consume {
                sharedViewModel.filesToShareFromIntent.value = arrayListOf()
                sharedViewModel.textToShareFromIntent.value = ""
            }
        }

        sharedViewModel.filesToShareFromIntent.observe(this) { list ->
            if (list.isNotEmpty()) {
                viewModel.addFilesPendingSharing(list)
            } else {
                viewModel.filesOrTextPendingSharingListCleared()
            }
        }

        sharedViewModel.textToShareFromIntent.observe(this) { text ->
            if (!text.isEmpty()) {
                viewModel.addTextPendingSharing()
            } else {
                viewModel.filesOrTextPendingSharingListCleared()
            }
        }

        // Wait for latest visited fragment to be displayed before hiding the splashscreen
        binding.root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (navigatedToDefaultFragment) {
                    Log.i("$TAG Report UI has been fully drawn (TTFD)")
                    try {
                        reportFullyDrawn()
                    } catch (se: SecurityException) {
                        Log.e("$TAG Security exception when doing reportFullyDrawn(): $se")
                    }
                    binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                    true
                } else {
                    false
                }
            }
        })
        coreContext.bearerAuthenticationRequestedEvent.observe(this) {
            it.consume { pair ->
                val serverUrl = pair.first
                val username = pair.second

                Log.i(
                    "$TAG Bearer auth request, navigating to Single Sign On Fragment with server URL [$serverUrl] and username [$username]"
                )
                val intent = Intent(this, SingleSignOnActivity::class.java)
                intent.putExtra(SingleSignOnActivity.INTENT_EXTRA_USERNAME, username)
                intent.putExtra(SingleSignOnActivity.INTENT_EXTRA_SERVER_URL, serverUrl)
                startActivity(intent)
            }
        }

        coreContext.digestAuthenticationRequestedEvent.observe(this) {
            it.consume { identity ->
                try {
                    if (coreContext.digestAuthInfoPendingPasswordUpdate != null) {
                        showAuthenticationRequestedDialog(identity)
                    }
                } catch (e: WindowManager.BadTokenException) {
                    Log.e("$TAG Failed to show authentication dialog: $e")
                }
            }
        }

        coreContext.clearAuthenticationRequestDialogEvent.observe(this) {
            it.consume {
                currentlyDisplayedAuthDialog?.dismiss()
            }
        }

        coreContext.showGreenToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showGreenToast(message, icon)
            }
        }

        coreContext.showRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        coreContext.showFormattedRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        coreContext.provisioningAppliedEvent.observe(this) {
            it.consume {
                Log.i("$TAG Remote provisioning was applied, checking if theme has changed")
                checkMainColorTheme()
            }
        }

        coreContext.mdmConfigRemovedEvent.observe(this) {
            it.consume {
                Log.i("$TAG Managed configuration applied, checking remaining accounts")
                viewModel.onMdmConfigRemoved()
            }
        }

        coreContext.filesToExportToNativeMediaGalleryEvent.observe(this) {
            it.consume { files ->
                Log.i("$TAG Found [${files.size}] files to export to native media gallery")
                for (file in files) {
                    exportFileToNativeMediaGallery(file)
                }

                coreContext.postOnCoreThread {
                    coreContext.clearFilesToExportToNativeGallery()
                }
            }
        }

        CarConnection(this).type.observe(this) {
            val asString = when (it) {
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "NOT CONNECTED"
                CarConnection.CONNECTION_TYPE_PROJECTION -> "PROJECTION"
                CarConnection.CONNECTION_TYPE_NATIVE -> "NATIVE"
                else -> "UNEXPECTED ($it)"
            }
            Log.i("$TAG Car connection is [$asString]")
            val projection = it == CarConnection.CONNECTION_TYPE_PROJECTION
            coreContext.isConnectedToAndroidAuto = projection
        }

        coreContext.postOnCoreThread { core ->
            // Leader correction 2026-11-11 (round 2): drain queued actionable
            // intents BEFORE the intro decision so a destination that arrived
            // during the readiness wait interrupts/suppresses the intro
            val firstRunDestination: Class<*>? = when {
                corePreferences.firstLaunch -> {
                    Log.i("$TAG First time Linphone 6.0 has been started, showing Welcome activity after the intro")
                    corePreferences.firstLaunch = false
                    WelcomeActivity::class.java
                }
                core.accountList.isEmpty() -> {
                    Log.w("$TAG No account found, showing Assistant activity after the intro")
                    AssistantActivity::class.java
                }
                else -> null
            }
            coreContext.postOnMainThread {
                // Drain queued intents first; handleIntent marks the
                // interruption flag for anything actionable
                if (pendingIntentsWhileCoreNotReady.isNotEmpty()) {
                    val pending = pendingIntentsWhileCoreNotReady.toList()
                    pendingIntentsWhileCoreNotReady.clear()
                    Log.i("$TAG Draining [${pending.size}] queued intent(s) before intro decision")
                    for (pendingIntent in pending) {
                        if (isActionableIntent(pendingIntent)) {
                            markIntroInterruption()
                        }
                        handleIntent(pendingIntent)
                    }
                }
                if (actionableIntentInterruption) {
                    // The actionable destination wins: no intro and no
                    // first-run destination may cover it
                    Log.i("$TAG Actionable intent interruption recorded, suppressing intro and first-run destination")
                    showMyTelIntroIfNeeded(null, suppressed = true)
                } else if (firstRunDestination != null) {
                    showMyTelIntroIfNeeded(afterIntro = {
                        // Cancellation-aware: an actionable intent arriving
                        // while the intro plays cancels the destination start
                        if (actionableIntentInterruption) {
                            Log.i("$TAG Intro destination cancelled by actionable intent")
                            return@showMyTelIntroIfNeeded
                        }
                        try {
                            startActivity(Intent(this, firstRunDestination))
                        } catch (ise: IllegalStateException) {
                            Log.e("$TAG Can't start activity: $ise")
                        }
                    })
                } else {
                    showMyTelIntroIfNeeded()
                }
            }
        }

        // If onPostCreate ran before the core was ready, catch up now
        if (deferredOnPostCreate) {
            deferredOnPostCreate = false
            val deferredSavedState = deferredOnPostCreateSavedState
            deferredOnPostCreateSavedState = null
            Log.i("$TAG Core is now ready, running deferred onPostCreate tasks")
            runOnPostCreateTasks(deferredSavedState)
        }
        if (pendingIntentsWhileCoreNotReady.isNotEmpty()) {
            val pending = pendingIntentsWhileCoreNotReady.toList()
            pendingIntentsWhileCoreNotReady.clear()
            Log.i("$TAG Core is now ready, handling [${pending.size}] intent(s) that arrived earlier (post-intro leftovers)")
            for (pendingIntent in pending) {
                handleIntent(pendingIntent)
            }
        }

        // If the activity was already resumed while the core was not ready,
        // perform the onResume initialization that was skipped (leader
        // correction 2026-11-11)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            Log.i("$TAG Activity already resumed, catching up on onResume tasks")
            runOnResumeTasks()
        }
    }

    /** An intent that carries a destination (share, dial, view, call...). */
    private fun isActionableIntent(candidate: Intent?): Boolean {
        if (candidate == null) return false
        val plainLauncher = candidate.action == Intent.ACTION_MAIN &&
            candidate.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            candidate.extras == null
        return !plainLauncher
    }

    /** Records an actionable-intent interruption of the intro flow. */
    private fun markIntroInterruption() {
        if (!actionableIntentInterruption) {
            Log.i("$TAG Actionable intent interruption recorded, dismissing/suppressing intro")
            actionableIntentInterruption = true
        }
        introOverlay?.skip()
    }

    /**
     * Shows the MyTel branded intro overlay for an ordinary cold launcher
     * opening (leader decisions #1/#2, 2026-11-11). Bypassed for actionable
     * intents (calls, shares, shortcuts, history relaunch) and only ever
     * shown once per process. Every bypass path releases the held system
     * splash (leader correction 2026-11-11: recreation after a completed
     * intro must never leave the splash stuck). [afterIntro] runs once the
     * intro is gone (or immediately when no intro is shown) and is used to
     * open the first-run destination after the intro; it must re-check
     * [actionableIntentInterruption] itself (cancellation-aware).
     */
    private fun showMyTelIntroIfNeeded(afterIntro: (() -> Unit)? = null, suppressed: Boolean = false) {
        if (suppressed || actionableIntentInterruption) {
            introShownThisProcess = true
            keepSystemSplashVisible = false
            return
        }
        if (introShownThisProcess) {
            // Already played in this process (e.g. activity recreation):
            // never hold the system splash again
            keepSystemSplashVisible = false
            afterIntro?.invoke()
            return
        }
        // Consume the once-per-process slot even when conditions aren't met
        // so the intro never plays later by surprise
        introShownThisProcess = true

        val launcherIntent = intent
        val isOrdinaryLauncherOpening = launcherIntent != null &&
            launcherIntent.action == Intent.ACTION_MAIN &&
            launcherIntent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            launcherIntent.extras == null &&
            (launcherIntent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (!isOrdinaryLauncherOpening) {
            Log.i("$TAG Not an ordinary cold launcher opening, skipping intro overlay")
            keepSystemSplashVisible = false
            afterIntro?.invoke()
            return
        }

        Log.i("$TAG Showing MyTel intro overlay")
        val overlay = MyTelIntroOverlay(this)
        val root = binding.root as android.view.ViewGroup
        overlay.onFinished = {
            root.removeView(overlay)
            if (introOverlay === overlay) {
                introOverlay = null
            }
            keepSystemSplashVisible = false
            afterIntro?.invoke()
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        introOverlay = overlay
        overlay.start()

        // The intro overlay's first frame (badge on navy, matching the
        // system splash) can now replace the system splash seamlessly
        keepSystemSplashVisible = false
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        if (!::viewModel.isInitialized) {
            Log.i("$TAG Core not ready yet, deferring onPostCreate tasks")
            deferredOnPostCreate = true
            deferredOnPostCreateSavedState = savedInstanceState
            return
        }

        runOnPostCreateTasks(savedInstanceState)
    }

    private fun runOnPostCreateTasks(savedInstanceState: Bundle?) {
        goToLatestVisitedFragment()

        // We don't want that intent to be handled upon rotation
        if (savedInstanceState == null && intent != null) {
            Log.d("$TAG savedInstanceState is null but intent isn't, handling it")
            handleIntent(intent)
        }

        if (corePreferences.useCallRedirectionService) {
            Log.i(
                "$TAG Call redirection service enabled, making sure we have the required permissions and role granted"
            )
            requestCallRedirectionPermissionsAndRole()
        }
    }

    override fun onPause() {
        // Anything taking over the screen (incoming call activity, etc.)
        // immediately dismisses the intro overlay (leader decision #2)
        introOverlay?.skip()

        if (!::viewModel.isInitialized) {
            Log.w("$TAG Core not ready yet, skipping onPause tasks")
            super.onPause()
            return
        }

        viewModel.enableAccountMonitoring(false)

        currentlyDisplayedAuthDialog?.dismiss()
        currentlyDisplayedAuthDialog = null

        val defaultFragmentId = when (sharedViewModel.currentlyDisplayedFragment.value) {
            R.id.contactsListFragment -> {
                CONTACTS_FRAGMENT_ID
            }
            R.id.historyListFragment -> {
                HISTORY_FRAGMENT_ID
            }
            R.id.conversationsListFragment -> {
                CHAT_FRAGMENT_ID
            }
            R.id.meetingsListFragment -> {
                MEETINGS_FRAGMENT_ID
            }
            else -> { // Default
                HISTORY_FRAGMENT_ID
            }
        }
        getPreferences(MODE_PRIVATE).edit {
            putInt(DEFAULT_FRAGMENT_KEY, defaultFragmentId)
        }
        Log.i("$TAG Stored [$defaultFragmentId] as default page")

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        if (!::viewModel.isInitialized) {
            Log.w("$TAG Core not ready yet, skipping onResume tasks")
            return
        }

        runOnResumeTasks()
    }

    private fun runOnResumeTasks() {
        viewModel.enableAccountMonitoring(true)
        viewModel.updateMissingPermissionAlert()
        viewModel.updateAccountsAndNetworkReachability()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("$TAG Handling new intent")
        // Leader correction (round 2): actionable intents interrupt the
        // intro BEFORE any handling, so the destination is never covered
        if (isActionableIntent(intent)) {
            markIntroInterruption()
        }
        if (!::viewModel.isInitialized) {
            Log.i("$TAG Core not ready yet, deferring new intent handling")
            pendingIntentsWhileCoreNotReady.add(intent)
            return
        }
        handleIntent(intent)
    }

    override fun onDestroy() {
        coreReadyHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REDIRECT_ROLE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.i("$TAG App was granted ROLE_CALL_REDIRECTION")
            } else {
                Log.w("$TAG App wasn't granted ROLE_CALL_REDIRECTION, toggling OFF Call Redirection setting")
                corePreferences.useCallRedirectionService = false
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @SuppressLint("RtlHardcoded")
    fun toggleDrawerMenu() {
        if (binding.drawerMenu.isDrawerOpen(Gravity.LEFT)) {
            closeDrawerMenu()
        } else {
            openDrawerMenu()
        }
    }

    fun closeDrawerMenu() {
        binding.drawerMenu.closeDrawer(binding.drawerMenuContent, true)
    }

    private fun openDrawerMenu() {
        binding.drawerMenu.openDrawer(binding.drawerMenuContent, true)
    }

    fun findNavController(): NavController {
        return findNavController(R.id.main_nav_container)
    }

    fun loadContacts() {
        coreContext.contactsManager.loadContacts(this)
    }

    fun requestCallRedirectionPermissionsAndRole() {
        Log.i("$TAG Requesting READ_PHONE_STATE and READ_PHONE_NUMBERS permissions needed for Call Redirection feature")
        requestReadPhonePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS))
    }

    private fun goToLatestVisitedFragment() {
        try {
            // Prevent navigating to default fragment upon rotation (we only want to do it on first start)
            if (intent.action == Intent.ACTION_MAIN && intent.type == null && intent.data == null) {
                if (viewModel.mainIntentHandled) {
                    Log.d(
                        "$TAG Main intent without type nor data was already handled, do nothing"
                    )
                    navigatedToDefaultFragment = true
                    return
                } else {
                    viewModel.mainIntentHandled = true
                }
            }

            val defaultFragmentId = getPreferences(MODE_PRIVATE).getInt(
                DEFAULT_FRAGMENT_KEY,
                HISTORY_FRAGMENT_ID
            )
            Log.i(
                "$TAG Trying to navigate to set default destination [$defaultFragmentId]"
            )
            try {
                val navOptionsBuilder = NavOptions.Builder()
                navOptionsBuilder.setPopUpTo(R.id.historyListFragment, true)
                navOptionsBuilder.setLaunchSingleTop(true)
                val navOptions = navOptionsBuilder.build()
                val args = bundleOf()
                when (defaultFragmentId) {
                    CONTACTS_FRAGMENT_ID -> {
                        findNavController().addOnDestinationChangedListener(destinationListener)
                        findNavController().navigate(
                            R.id.contactsListFragment,
                            args,
                            navOptions
                        )
                    }
                    CHAT_FRAGMENT_ID -> {
                        findNavController().addOnDestinationChangedListener(destinationListener)
                        findNavController().navigate(
                            R.id.conversationsListFragment,
                            args,
                            navOptions
                        )
                    }
                    MEETINGS_FRAGMENT_ID -> {
                        findNavController().addOnDestinationChangedListener(destinationListener)
                        findNavController().navigate(
                            R.id.meetingsListFragment,
                            args,
                            navOptions
                        )
                    }
                    else -> {
                        Log.i("$TAG Default fragment is the same as the latest visited one")
                        navigatedToDefaultFragment = true
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Can't navigate to Conversations fragment: $ise")
            }
        } catch (ise: IllegalStateException) {
            Log.i("$TAG Failed to handle intent: $ise")
        }
    }

    private fun handleIntent(intent: Intent) {
        val extras = intent.extras
        val hasExtra = extras != null && !extras.isEmpty
        Log.i(
            "$TAG Handling intent action [${intent.action}], type [${intent.type}], data [${intent.data}] and has ${if (hasExtra) "extras" else "no extra"}"
        )

        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_SEND -> {
                handleSendIntent(intent, false)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendIntent(intent, true)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data?.toString() ?: ""
                if (uri.startsWith("linphone-config:")) {
                    handleConfigIntent(uri)
                } else {
                    handleCallIntent(intent)
                }
            }
            Intent.ACTION_DIAL, Intent.ACTION_CALL -> {
                handleCallIntent(intent)
            }
            Intent.ACTION_VIEW_LOCUS -> {
                val locus = Compatibility.extractLocusIdFromIntent(intent)
                if (locus != null) {
                    Log.i("$TAG Found chat room locus intent extra: $locus")
                    handleLocusOrShortcut(locus)
                }
            }
            else -> {
                handleMainIntent(intent)
            }
        }
    }

    private fun handleLocusOrShortcut(id: String) {
        Log.i("$TAG Found locus ID [$id]")
        if (id.isNotEmpty()) {
            Log.i("$TAG Navigating to conversation with ID [$id], computed from shortcut ID")
            sharedViewModel.showConversationEvent.value = Event(id)
        }
    }

    private fun handleMainIntent(intent: Intent) {
        coreContext.postOnCoreThread { core ->
            if (intent.hasExtra(ARGUMENTS_CHAT)) {
                Log.i("$TAG Intent has [Chat] extra")
                coreContext.postOnMainThread {
                    try {
                        Log.i("$TAG Trying to go to Conversations fragment")
                        val args = intent.extras
                        val conversationId = args?.getString(ARGUMENTS_CONVERSATION_ID, "")
                        if (conversationId.isNullOrEmpty()) {
                            Log.w("$TAG Found [Chat] extra but no conversation ID!")
                        } else {
                            Log.i("$TAG Found [Chat] extra with conversation ID [$conversationId]")
                            sharedViewModel.showConversationEvent.value = Event(conversationId)
                        }
                        args?.clear()

                        if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                            Log.w(
                                "$TAG Current destination is already conversations list, skipping navigation"
                            )
                        } else {
                            val navOptionsBuilder = NavOptions.Builder()
                            navOptionsBuilder.setPopUpTo(
                                findNavController().currentDestination?.id ?: R.id.historyListFragment,
                                true
                            )
                            navOptionsBuilder.setLaunchSingleTop(true)
                            val navOptions = navOptionsBuilder.build()
                            findNavController().navigate(
                                R.id.conversationsListFragment,
                                args,
                                navOptions
                            )
                        }
                    } catch (ise: IllegalStateException) {
                        Log.e("$TAG Can't navigate to Conversations fragment: $ise")
                    }
                }
            }
        }
    }

    private fun handleSendIntent(intent: Intent, multiple: Boolean) {
        val parcelablesUri = arrayListOf<Uri>()
        if (multiple) {
            val parcelables =
                intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            for (parcelable in parcelables.orEmpty()) {
                val uri = parcelable as? Uri
                if (uri != null) {
                    Log.i("$TAG Found URI [$uri] in parcelable extra list")
                    parcelablesUri.add(uri)
                }
            }
        } else {
            val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            if (uri != null) {
                Log.i("$TAG Found URI [$uri] in parcelable extra")
                parcelablesUri.add(uri)
            }
        }

        val list = arrayListOf<String>()
        lifecycleScope.launch {
            val deferred = arrayListOf<Deferred<String?>>()
            for (uri in parcelablesUri) {
                Log.i("$TAG Deferring copy from file [${uri.path}] to local storage")
                deferred.add(async { FileUtils.getFilePath(this@MainActivity, uri, false) })
            }

            if (binding.drawerMenu.isOpen) {
                Log.i("$TAG Drawer menu is opened, closing it")
                closeDrawerMenu()
            }
            if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                if (sharedViewModel.displayedChatRoom != null) {
                    Log.w(
                        "$TAG Closing already opened conversation to prevent attaching file in it directly"
                    )
                    sharedViewModel.hideConversationEvent.value = Event(true)
                } else {
                    Log.i("$TAG No chat room currently displayed, nothing to close")
                }
            }

            val paths = deferred.awaitAll()
            for (path in paths) {
                Log.i("$TAG Found file to share [$path] in intent")
                if (path != null) list.add(path)
            }

            var textToShare = ""
            if (intent.type == "text/plain") {
                Log.i("$TAG Intent type is [${intent.type}], expecting text in Intent.EXTRA_TEXT")
                textToShare = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (textToShare.isEmpty()) {
                    Log.e("$TAG Intent.EXTRA_TEXT not found in intent!")
                } else {
                    Log.i("$TAG Found extra text in intent, long of [${textToShare.length}]")
                }
            }

            if (list.isNotEmpty()) {
                sharedViewModel.filesToShareFromIntent.postValue(list)
            } else {
                if (textToShare.isNotEmpty()) {
                    Log.i("$TAG Found plain text to share")
                    sharedViewModel.textToShareFromIntent.postValue(textToShare)
                } else {
                    Log.w("$TAG Failed to find at least one file or text to share!")
                }
            }

            if (findNavController().currentDestination?.id == R.id.debugFragment) {
                Log.i(
                    "$TAG App is already started and in debug fragment, navigating to conversations list"
                )
                val conversationId = parseShortcutIfAny(intent)
                if (conversationId != null) {
                    Log.i(
                        "$TAG Navigating from debug to conversation with ID [$conversationId], computed from shortcut ID"
                    )
                    sharedViewModel.showConversationEvent.postValue(Event(conversationId))
                }

                val action = ConversationsListFragmentDirections.actionGlobalConversationsListFragment()
                val options = NavOptions.Builder()
                options.apply {
                    setPopUpTo(R.id.helpFragment, true)
                    setLaunchSingleTop(true)
                }
                findNavController().navigate(action, options.build())
            } else {
                val conversationId = parseShortcutIfAny(intent)
                if (conversationId != null) {
                    Log.i(
                        "$TAG Navigating to conversation with conversation ID [$conversationId] addresses, computed from shortcut ID"
                    )
                    sharedViewModel.showConversationEvent.postValue(Event(conversationId))
                }

                if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                    Log.w(
                        "$TAG Current destination is already conversations list, skipping navigation"
                    )
                } else {
                    val action = ConversationsListFragmentDirections.actionGlobalConversationsListFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }

    private fun parseShortcutIfAny(intent: Intent): String? {
        val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID") // Intent.EXTRA_SHORTCUT_ID
        if (shortcutId != null) {
            Log.i("$TAG Found shortcut ID [$shortcutId]")
            return shortcutId
        } else {
            Log.i("$TAG No shortcut ID was found")
        }
        return null
    }

    private fun handleCallIntent(intent: Intent) {
        val uri = intent.data?.toString()
        if (uri.isNullOrEmpty()) {
            Log.e("$TAG Intent data is null or empty, can't process [${intent.action}] intent")
            return
        }

        Log.i("$TAG Found URI [$uri] as data for intent [${intent.action}]")
        val sipUriToCall = when {
            uri.startsWith("tel:") -> uri.substring("tel:".length)
            uri.startsWith("callto:") -> uri.substring("callto:".length)
            uri.startsWith("sip-linphone:") -> uri.replace("sip-linphone:", "sip:")
            uri.startsWith("linphone-sip:") -> uri.replace("linphone-sip:", "sip:")
            uri.startsWith("sips-linphone:") -> uri.replace("sips-linphone:", "sips:")
            uri.startsWith("linphone-sips:") -> uri.replace("linphone-sips:", "sips:")
            else -> uri.replace("%40", "@") // Unescape @ character if needed
        }

        coreContext.postOnCoreThread {
            val address = coreContext.core.interpretUrl(
                sipUriToCall,
                LinphoneUtils.applyInternationalPrefix()
            )
            Log.i("$TAG Interpreted SIP URI is [${address?.asStringUriOnly()}]")
            if (address != null) {
                // Skip network reachability test, this code will be called too soon
                coreContext.startAudioCall(address, skipNetworkReachabilityTest = true)
            }
        }
    }

    private fun handleConfigIntent(uri: String) {
        Log.i("$TAG Trying to parse config intent [$uri] as remote provisioning URL")
        val url = LinphoneUtils.getRemoteProvisioningUrlFromUri(uri)
        if (url == null) {
            Log.e("$TAG Couldn't parse URI [$uri] into a valid remote provisioning URL, aborting")
            return
        }

        coreContext.postOnCoreThread { core ->
            core.provisioningUri = url
            Log.w("$TAG Remote provisioning URL set to [$url], restarting Core now")
            core.stop()
            Log.i("$TAG Core has been stopped, let's restart it")
            core.start()
            Log.i("$TAG Core has been restarted")
        }
    }

    private fun showAuthenticationRequestedDialog(identity: String) {
        currentlyDisplayedAuthDialog?.dismiss()

        val label = AppUtils.getFormattedString(
            R.string.account_settings_dialog_invalid_password_message,
            identity
        )
        val model = PasswordDialogModel(label)
        val dialog = DialogUtils.getAuthRequestedDialog(this, model)

        model.dismissEvent.observe(this) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(this) {
            it.consume { password ->
                coreContext.postOnCoreThread {
                    coreContext.updateAuthInfo(password)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
        currentlyDisplayedAuthDialog = dialog
    }

    private fun exportFileToNativeMediaGallery(filePath: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Log.i("$TAG Export file [$filePath] to Android's MediaStore")
                val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                if (mediaStorePath.isNotEmpty()) {
                    Log.i("$TAG File [$filePath] has been successfully exported to MediaStore")
                } else {
                    Log.e("$TAG Failed to export file [$filePath] to MediaStore!")
                }
            }
        }
    }

    private fun requestCallRedirectionRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager: RoleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            // Check if the app needs to register call redirection role.
            val shouldRequestRole = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)
            if (shouldRequestRole) {
                Log.i("$TAG Starting intent to request Linphone be used as Call Redirection")
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
                startActivityForResult(intent, REDIRECT_ROLE_REQUEST_CODE)
            } else {
                Log.i("$TAG Linphone is already defined as Call Redirection")
            }
        } else {
            Log.e("$TAG Call Redirection feature requires Android Q (API 29) or higher")
            corePreferences.useCallRedirectionService = false
        }
    }
}
