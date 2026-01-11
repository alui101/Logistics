package com.example.logistics

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun AppNavigation(
    navController: NavHostController,
    auth: FirebaseAuth,
    startDestination: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var firstBackPressTime by remember { mutableLongStateOf(0L) }
    var isNavigating by remember { mutableStateOf(false) }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // Reset navigation flag when back stack changes
    LaunchedEffect(currentBackStackEntry) {
        isNavigating = false
    }

    // Check if we're at a root dashboard
    val isAtRoot = currentBackStackEntry?.destination?.route in listOf(
        Constants.ROUTE_ADMIN,
        Constants.ROUTE_MANAGER,
        Constants.ROUTE_DRIVER
    )

    // Global back handler
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()

        // Prevent rapid double-taps
        if (isNavigating || currentTime - lastBackPressTime < 500) {
            return@BackHandler
        }

        lastBackPressTime = currentTime

        if (isAtRoot) {
            // At root - double tap to exit
            val timeSinceFirstPress = currentTime - firstBackPressTime
            if (timeSinceFirstPress < 2000 && firstBackPressTime > 0) {
                // Second back press within 2 seconds - exit app
                firstBackPressTime = 0 // Reset
                val activity = context.findActivity()
                activity?.finish()
            } else {
                // First back press - show message and record time
                firstBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Not at root - pop back stack with debouncing
            isNavigating = true
            navController.popBackStack()
            scope.launch {
                delay(300)
                isNavigating = false
            }
        }
    }

    // Provide debounced navigation function for TopAppBar back buttons
    val debouncedPopBackStack: () -> Unit = {
        if (!isNavigating) {
            // Check if screen is resumed before navigating
            val isResumed = navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
            if (isResumed && navController.previousBackStackEntry != null) {
                isNavigating = true
                navController.popBackStack()
                scope.launch {
                    delay(500)
                    isNavigating = false
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        // Route: Login
        composable(Constants.ROUTE_LOGIN) {
            val loginContext = LocalContext.current

            LoginScreen(
                context = loginContext,
                onLogin = { email, password ->
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            val prefs = UserPrefs(loginContext)
                            prefs.saveUser(email, password)
                            
                            // Log the login - get role from Firestore
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            result.user?.uid?.let { userId ->
                                db.collection(Constants.COLLECTION_USERS)
                                    .document(userId)
                                    .get()
                                    .addOnSuccessListener { doc ->
                                        val role = doc.getString("role") ?: "unknown"
                                        AuthLogger.logLogin(userId, email, role)
                                    }
                                    .addOnFailureListener {
                                        // Log with unknown role if we can't fetch it
                                        AuthLogger.logLogin(userId, email, "unknown")
                                    }
                            }
                            
                            navController.navigate(Constants.ROUTE_LOADING) {
                                popUpTo(Constants.ROUTE_LOGIN) { inclusive = true }
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(loginContext, "Login failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            )
        }

        // Route: Loading
        composable(Constants.ROUTE_LOADING) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            RoleRouter(auth = auth, db = db, navController = navController)
        }

        // Route: Dashboards
        composable(Constants.ROUTE_ADMIN) {
            AdminDashboard(
                navController = navController,
                auth = auth,
                onNavigateBack = debouncedPopBackStack
            )
        }

        composable(Constants.ROUTE_MANAGER) {
            TripManagementScreen(
                navController = navController,
                auth = auth,
                isManagerRoot = true,
                onNavigateBack = debouncedPopBackStack
            )
        }

        composable(Constants.ROUTE_DRIVER) {
            DriverDashboard(
                navController = navController,
                auth = auth,
                onNavigateBack = debouncedPopBackStack
            )
        }

        // Route: Sub-screens
        composable(Constants.ROUTE_ADD_USER) {
            AddUserScreen(
                navController = navController,
                auth = auth,
                onNavigateBack = debouncedPopBackStack
            )
        }

        composable(Constants.ROUTE_MANAGE_USERS) {
            ManageUsersScreen(
                navController = navController,
                onNavigateBack = debouncedPopBackStack
            )
        }

        composable(Constants.ROUTE_MANAGE_VEHICLES) {
            ManageVehiclesScreen(
                navController = navController,
                onNavigateBack = debouncedPopBackStack
            )
        }

        composable(Constants.ROUTE_ADMIN_TRIPS) {
            TripManagementScreen(
                navController = navController,
                auth = auth,
                isManagerRoot = false,
                onNavigateBack = debouncedPopBackStack
            )
        }
        composable("${Constants.ROUTE_DRIVER_TRIP_DETAIL}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            DriverTripDetailScreen(
                navController = navController,
                tripId = tripId,
                // Removed 'auth = auth' because the screen doesn't ask for it
                onNavigateBack = { navController.popBackStackSafe() }
            )
        }
        composable(Constants.ROUTE_DRIVER_MENU) {
            MenuScreen(navController = navController, auth = auth)
        }

        // 2. Media Manager Route
        composable(Constants.ROUTE_MEDIA_MANAGER) {
            MediaManagerScreen(
                navController = navController,
                onNavigateBack = debouncedPopBackStack
            )
        }

        // 3. Trip Verification Route
        composable("${Constants.ROUTE_TRIP_VERIFICATION}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TripVerificationScreen(
                navController = navController,
                tripId = tripId,
                onNavigateBack = debouncedPopBackStack
            )
        }
    }
}
fun androidx.navigation.NavController.popBackStackSafe() {
    if (currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
