package com.franklinprakash.epiclibraryviewer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.franklinprakash.epiclibraryviewer.databinding.ActivityMainBinding
import com.franklinprakash.epiclibraryviewer.model.EpicGame
import com.franklinprakash.epiclibraryviewer.model.EpicTokenResponse
import com.franklinprakash.epiclibraryviewer.security.SecurePrefs
import com.franklinprakash.epiclibraryviewer.ui.GameAdapter
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val code = it.data?.getStringExtra(EpicLoginActivity.EXTRA_CODE)
                if (code != null) exchangeCode(code)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.loginButton.setOnClickListener { startLogin() }
        binding.refreshButton.setOnClickListener { loadLibrary() }

        if (SecurePrefs.getToken(this) == null) {
            showLoggedOut()
        } else {
            loadLibrary()
        }
    }

    // 1️⃣ OAuth login
    private fun startLogin() {
        val intent = Intent(this, EpicLoginActivity::class.java)
        intent.putExtra(
            "auth_url",
            "https://www.epicgames.com/id/api/redirect" +
                    "?clientId=34a02cf8f4414e29b15921876da36f9a" +
                    "&responseType=code"
        )
        loginLauncher.launch(intent)
    }

    // 2️⃣ Exchange code → token
    private fun exchangeCode(code: String) = CoroutineScope(Dispatchers.IO).launch {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("token_type", "eg1")
            .build()

        val request = Request.Builder()
            .url("https://account-public-service-prod.ol.epicgames.com/account/api/oauth/token")
            .post(body)
            .header(
                "Authorization",
                "Basic BASE64_CLIENTID_SECRET"
            )
            .build()

        val response = client.newCall(request).execute()
        val json = response.body?.string() ?: return@launch

        val token = Json.decodeFromString<EpicTokenResponse>(json)
        SecurePrefs.saveToken(this@MainActivity, token.accessToken)

        withContext(Dispatchers.Main) { loadLibrary() }
    }

    // 3️⃣ Fetch Epic library
    private fun loadLibrary() = CoroutineScope(Dispatchers.IO).launch {
        val token = SecurePrefs.getToken(this@MainActivity) ?: return@launch

        val request = Request.Builder()
            .url("https://library-service.live.use1a.on.epicgames.com/library/api/public/items")
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        val json = response.body?.string() ?: return@launch

        val jsonParser = Json { ignoreUnknownKeys = true }
        val games = jsonParser.decodeFromString<List<EpicGame>>(json)

        withContext(Dispatchers.Main) { showLibrary(games) }
    }

    // 4️⃣ UI states
    private fun showLoggedOut() {
        binding.loginButton.visibility = View.VISIBLE
        binding.refreshButton.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.statusText.text = "Not logged in"
    }

    private fun showLibrary(games: List<EpicGame>) {
        binding.loginButton.visibility = View.GONE
        binding.refreshButton.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.VISIBLE
        binding.recyclerView.adapter = GameAdapter(games)
        binding.statusText.text = "Games: ${games.size}"
    }
}