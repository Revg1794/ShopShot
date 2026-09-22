package com.fullstackit.shopshot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fullstackit.shopshot.ui.ShopShotRoot
import com.fullstackit.shopshot.ui.theme.ShopShotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShopShotTheme {
                ShopShotRoot()
            }
        }
    }
}
