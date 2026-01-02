package com.franklinprakash.epiclibraryviewer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var loginButton: Button
    private lateinit var refreshButton: Button
    private lateinit var statusText: TextView
    private lateinit var userNameText: TextView
    private lateinit var emptyView: TextView

    private lateinit var libraryManager: EpicLibraryManager
    private val gamesAdapter = GamesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()

        libraryManager = EpicLibraryManager(this)

        checkLoginStatus()
        intent?.let { handleIntent(it) }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        loginButton = findViewById(R.id.loginButton)
        refreshButton = findViewById(R.id.refreshButton)
        statusText = findViewById(R.id.statusText)
        userNameText = findViewById(R.id.userNameText)
        emptyView = findViewById(R.id.emptyView)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = gamesAdapter
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            initiateLogin()
        }

        refreshButton.setOnClickListener {
            refreshLibrary(forceRefresh = true)
        }
    }

    private fun checkLoginStatus() {
        lifecycleScope.launch {
            showLoading(true, getString(R.string.initializing))

            val isLoggedIn = libraryManager.initialize()

            if (isLoggedIn) {
                updateUIForLoggedIn()
                refreshLibrary(forceRefresh = false)
            } else {
                updateUIForLoggedOut()
                showLoading(false)
            }
        }
    }

    private fun initiateLogin() {
        val authUrl = libraryManager.getAuthorizationUrl()

        Toast.makeText(
            this,
            getString(R.string.logging_in),
            Toast.LENGTH_SHORT
        ).show()

        val intent = Intent(Intent.ACTION_VIEW, authUrl.toUri())
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        try {
            val data = intent.data
            Log.d("MainActivity", "Handling intent. Action: ${intent.action}, Data: $data")
            if (data == null) {
                Log.w("MainActivity", "Intent data is null.")
                return
            }

            if (data.scheme == "epicgames" && data.host == "callback") {
                Log.d("MainActivity", "Received callback from Epic Games")
                val code = data.getQueryParameter("code")
                val error = data.getQueryParameter("error")
                Log.d("MainActivity", "Code: $code, Error: $error")

                when {
                    code != null -> completeAuthentication(code)
                    error != null -> showError(getString(R.string.login_cancelled_or_failed, error))
                    else -> {
                        Log.w("MainActivity", "Invalid response from Epic Games. No code or error.")
                        showError(getString(R.string.invalid_response))
                    }
                }
            } else {
                Log.w("MainActivity", "Intent received with unexpected scheme or host: $data")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing redirect", e)
            showError("Error processing login: ${e.message}")
        }
    }

    private fun completeAuthentication(authCode: String) {
        lifecycleScope.launch {
            showLoading(true, getString(R.string.authenticating))

            when (val result = libraryManager.authenticateWithCode(authCode)) {
                is AuthResult.Success -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.welcome_user, result.displayName),
                        Toast.LENGTH_SHORT
                    ).show()

                    updateUIForLoggedIn()
                    refreshLibrary(forceRefresh = true)
                }

                is AuthResult.Error -> {
                    showError(getString(R.string.login_failed, result.message))
                    updateUIForLoggedOut()
                    showLoading(false)
                }
            }
        }
    }

    private fun refreshLibrary(forceRefresh: Boolean) {
        lifecycleScope.launch {
            showLoading(true, if (forceRefresh) getString(R.string.refreshing_library) else getString(R.string.loading_library))

            when (val result = libraryManager.fetchLibrary(forceRefresh)) {
                is LibraryResult.Success -> {
                    displayGames(result.games)
                    statusText.text = getString(R.string.library_loaded, result.games.size)
                    showLoading(false)
                }

                is LibraryResult.Cached -> {
                    displayGames(result.games)
                    val timeAgo = getTimeAgo(result.lastSync)
                    statusText.text = getString(R.string.cached_library, result.games.size, timeAgo)
                    showLoading(false)

                    if (!forceRefresh) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            getString(R.string.showing_cached_data),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }

                is LibraryResult.Error -> {
                    showError(getString(R.string.failed_to_load_library, result.message))
                    statusText.text = getString(R.string.error_loading_library)
                    showLoading(false)
                }
            }
        }
    }

    private fun displayGames(games: List<Game>) {
        gamesAdapter.updateGames(games)

        if (games.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.no_games_found)
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun updateUIForLoggedIn() {
        loginButton.visibility = View.GONE
        refreshButton.visibility = View.VISIBLE

        val displayName = libraryManager.getDisplayName() ?: "User"
        userNameText.text = getString(R.string.logged_in_as, displayName)
        userNameText.visibility = View.VISIBLE
    }

    private fun updateUIForLoggedOut() {
        loginButton.visibility = View.VISIBLE
        refreshButton.visibility = View.GONE
        recyclerView.visibility = View.GONE
        userNameText.visibility = View.GONE
        emptyView.visibility = View.GONE
        statusText.text = getString(R.string.please_log_in)
    }

    private fun showLoading(loading: Boolean, message: String = "") {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        refreshButton.isEnabled = !loading
        loginButton.isEnabled = !loading

        if (loading && message.isNotEmpty()) {
            statusText.text = message
        }
    }

    private fun showError(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Retry") {
            if (libraryManager.isLoggedIn()) {
                refreshLibrary(true)
            } else {
                initiateLogin()
            }
        }.show()
    }

    private fun getTimeAgo(date: java.util.Date): String {
        val diff = System.currentTimeMillis() - date.time
        val minutes = (diff / (1000 * 60)).toInt()
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> getString(R.string.time_ago_just_now)
            minutes < 60 -> getString(R.string.time_ago_minutes, minutes)
            hours < 24 -> resources.getQuantityString(R.plurals.time_ago_hours, hours, hours)
            else -> resources.getQuantityString(R.plurals.time_ago_days, days, days)
        }
    }
}