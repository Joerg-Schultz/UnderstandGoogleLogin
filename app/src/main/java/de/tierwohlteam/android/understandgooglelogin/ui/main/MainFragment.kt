package de.tierwohlteam.android.understandgooglelogin.ui.main

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import de.tierwohlteam.android.understandgooglelogin.R
import de.tierwohlteam.android.understandgooglelogin.databinding.MainFragmentBinding

val TAG = "ONETAP"
val REQ_ONE_TAP = 111
private var showOneTapUI = true
private var isLoggedIn = false

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val statisticsViewModel: MainViewModel by activityViewModels()

    // https://developers.google.com/identity/one-tap/android/get-saved-credentials#kotlin
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    private var _binding: MainFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        oneTapClient = Identity.getSignInClient(view.context) // does this have to be an activity??
        signInRequest = BeginSignInRequest.builder()
            .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                .setSupported(true)
                .build())
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId(getString(R.string.client_id))
                    // Only show accounts previously used to sign in.
                    //.setFilterByAuthorizedAccounts(true)
                    .setFilterByAuthorizedAccounts(false)
                    .build())
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build()

        binding.btnLogin.setOnClickListener {
            if (isLoggedIn) {
                oneTapClient.signOut()
                isLoggedIn = false
                binding.btnLogin.text = "Login"
                binding.message.text = "You are logged out"
            } else {
                activity?.let { it1 ->
                    oneTapClient.beginSignIn(signInRequest)
                        .addOnSuccessListener(it1) { result ->
                            try {
                                startIntentSenderForResult(
                                    result.pendingIntent.intentSender, REQ_ONE_TAP,
                                    null, 0, 0, 0, null
                                )
                            } catch (e: IntentSender.SendIntentException) {
                                Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                            }
                        }
                        .addOnFailureListener(it1) { e ->
                            // No saved credentials found. Launch the One Tap sign-up flow, or
                            // do nothing and continue presenting the signed-out UI.
                            Log.d(TAG, e.localizedMessage)
                        }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ONE_TAP -> {
                try {
                    val credential = oneTapClient.getSignInCredentialFromIntent(data)
                    val idToken = credential.googleIdToken
                    val username = credential.id
                    val password = credential.password
                    when {
                        idToken != null -> {
                            // Got an ID token from Google. Use it to authenticate
                            // with your backend.
                            Log.d(TAG, "Got ID token\n $idToken")
                            isLoggedIn = true
                            binding.btnLogin.text = "Log Out"
                            binding.message.text = idToken
                        }
                        password != null -> {
                            // Got a saved username and password. Use them to authenticate
                            // with your backend.
                            Log.d(TAG, "Got password $password and username $username.")
                        }
                        else -> {
                            // Shouldn't happen.
                            Log.d(TAG, "No ID token or password!")
                        }
                    }
                } catch (e: ApiException) {
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> {
                            Log.d(TAG, "One-tap dialog was closed.")
                            // Don't re-prompt the user.
                            showOneTapUI = false
                        }
                        CommonStatusCodes.NETWORK_ERROR -> {
                            Log.d(TAG, "One-tap encountered a network error.")
                            // Try again or just ignore.
                        }
                        else -> {
                            Log.d(
                                TAG, "Couldn't get credential from result." +
                                        " (${e.localizedMessage})"
                            )
                        }
                    }
                }
            }
        }
    }
}