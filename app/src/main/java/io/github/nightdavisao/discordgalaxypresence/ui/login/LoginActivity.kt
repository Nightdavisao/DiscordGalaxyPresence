package io.github.nightdavisao.discordgalaxypresence.ui.login

import android.app.Activity
import android.content.SharedPreferences
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import io.github.nightdavisao.discordgalaxypresence.GameDetectionService
import io.github.nightdavisao.discordgalaxypresence.MFACodeDialogFragment
import io.github.nightdavisao.discordgalaxypresence.R
import io.github.nightdavisao.discordgalaxypresence.databinding.ActivityLoginBinding
import io.github.nightdavisao.discordgalaxypresence.discord.DiscordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    companion object {
        const val TAG = "LoginActivity"
    }

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferenceManager: SharedPreferences
    private val hCaptcha = HCaptcha.getClient(this)

    private fun handleMFAToken(ticket: String) {
        val mfaDialog = MFACodeDialogFragment()
        mfaDialog.setOkCallback { code ->
            try {
                withContext(Dispatchers.IO) {
                    val mfaToken = DiscordUtils.getTokenWithTotp(
                        code,
                        ticket
                    )
                    if (mfaToken != null) {
                        changeDiscordToken(mfaToken)
                        showLoginSuccess()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mfaDialog.show(this.supportFragmentManager, "dialog")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())[LoginViewModel::class.java]

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.exception != null) {
                if (loginResult.exception is DiscordUtils.MFAException) {
                    handleMFAToken(loginResult.exception.ticket)
                } else if (loginResult.exception is DiscordUtils.CaptchaException) {
                    hCaptcha.addOnSuccessListener { tokenResponse ->
                        try {
                            runBlocking {
                                val captchaToken = tokenResponse.tokenResult
                                val token = withContext(Dispatchers.IO) {
                                    DiscordUtils.getTokenWithCredentials(
                                        username.text.toString(),
                                        password.text.toString(),
                                        captchaToken
                                    )
                                }
                                if (token != null) {
                                    changeDiscordToken(token)
                                    showLoginSuccess()
                                }
                            }
                        } catch (e: DiscordUtils.MFAException) {
                            handleMFAToken(e.ticket)
                        }
                    }
                    hCaptcha.setup(HCaptchaConfig.builder()
                        .siteKey(loginResult.exception.siteKey)
                        .build())
                        .verifyWithHCaptcha()
                } else {
                    showLoginFailed(R.string.login_failed)
                }
            }

            if (loginResult.success != null) {
                changeDiscordToken(loginResult.success)
                showLoginSuccess()
            }
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> lifecycleScope.launch {
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                    }
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                lifecycleScope.launch {
                    loginViewModel.login(username.text.toString(), password.text.toString())
                }
            }
        }
    }

    private fun changeDiscordToken(token: String) {
        preferenceManager.edit()
            .putString("discord_token", token)
            .apply()
        GameDetectionService.instance?.changeDiscordToken(token)
    }

    private fun showLoginSuccess() {
        Toast.makeText(
            applicationContext,
            "You're now logged in",
            Toast.LENGTH_LONG
        ).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}