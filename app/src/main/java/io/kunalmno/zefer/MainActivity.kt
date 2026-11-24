package io.kunalmno.zefer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kunalmno.zefer.ar.ArCameraScreen
import io.kunalmno.zefer.ui.CatalogUiState
import io.kunalmno.zefer.ui.CatalogViewModel
import io.kunalmno.zefer.ui.common.ErrorScreen
import io.kunalmno.zefer.ui.common.LoadingScreen
import io.kunalmno.zefer.ui.home.HomeScreen
import io.kunalmno.zefer.ui.onboarding.OnboardingScreen
import io.kunalmno.zefer.ui.product.ProductScreen
import io.kunalmno.zefer.ui.theme.ZeferTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeferTheme {
                ZeferApp()
            }
        }
    }
}

@Composable
private fun ZeferApp(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showOnboarding by rememberSaveable { mutableStateOf(true) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var favorites by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var arOpen by rememberSaveable { mutableStateOf(false) }

    // Re-fetch whenever the app comes back to the foreground, so prices and deal
    // status (live / upcoming / ended) stay current. Ignored while a request is running.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    // System back: AR -> Product -> Home
    BackHandler(enabled = arOpen || selectedId != null) {
        if (arOpen) arOpen = false else selectedId = null
    }

    if (showOnboarding) {
        OnboardingScreen(onStartClick = { showOnboarding = false })
        return
    }

    when (val s = state) {
        CatalogUiState.Loading -> LoadingScreen()

        is CatalogUiState.Error -> ErrorScreen(message = s.message, onRetry = viewModel::retry)

        is CatalogUiState.Ready -> {
            val catalog = s.catalog
            val selected = catalog.products.firstOrNull { it.id == selectedId }

            fun toggleFavorite(id: Long) {
                favorites = if (id in favorites) favorites - id else favorites + id
            }

            if (selected != null && arOpen) {
                ArCameraScreen(product = selected, products = catalog.products, onBack = { arOpen = false })
            } else if (selected != null) {
                ProductScreen(
                    product = selected,
                    isFavorite = selected.id in favorites,
                    onBackClick = { selectedId = null },
                    onFavoriteClick = { toggleFavorite(selected.id) },
                    onTestArClick = { arOpen = true }
                )
            } else {
                HomeScreen(
                    products = catalog.products,
                    deals = catalog.deals,
                    favorites = favorites,
                    onDealClick = { selectedId = it.set.id },
                    onProductClick = { selectedId = it.id },
                    onFavoriteClick = { toggleFavorite(it.id) }
                )
            }
        }
    }
}
