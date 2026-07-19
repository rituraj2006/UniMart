package com.unimart.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.firebase.messaging.FirebaseMessaging
import com.unimart.app.activities.ChatActivity
import com.unimart.app.activities.ContactRequestsActivity
import com.unimart.app.activities.SellProductActivity
import com.unimart.app.activities.WishlistActivity
import com.unimart.app.databinding.ActivityMainBinding
import com.unimart.app.fragments.HomeFragment
import com.unimart.app.fragments.MessagesFragment
import com.unimart.app.fragments.ProfileFragment
import com.unimart.app.repositories.AuthRepository

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askNotificationPermission()
        updateFcmToken()

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_wishlist -> {
                    startActivity(Intent(this, WishlistActivity::class.java))
                    false // Don't select the tab as it's an Activity
                }
                R.id.nav_sell -> {
                    startActivity(Intent(this, SellProductActivity::class.java))
                    false // Don't select the tab as it's an Activity
                }
                R.id.nav_messages -> {
                    loadFragment(MessagesFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val fromNotification = intent.getBooleanExtra("fromNotification", false)
        if (fromNotification) {
            val type = intent.getStringExtra("type")
            val chatId = intent.getStringExtra("chatId")

            when (type) {
                "CHAT_REQUEST" -> {
                    startActivity(Intent(this, ContactRequestsActivity::class.java))
                }
                "MESSAGE" -> {
                    if (chatId != null) {
                        val chatIntent = Intent(this, ChatActivity::class.java).apply {
                            putExtra("CHAT_ID", chatId)
                        }
                        startActivity(chatIntent)
                    } else {
                        loadFragment(MessagesFragment())
                        binding.bottomNavigation.selectedItemId = R.id.nav_messages
                    }
                }
                else -> loadFragment(HomeFragment())
            }
        } else {
            loadFragment(HomeFragment())
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                AuthRepository().updateFcmToken(token)
            }
        }
    }
}
