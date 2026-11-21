package com.ohsung.marketbrief

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohsung.marketbrief.data.AppUpdate
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.UpdateChecker
import com.ohsung.marketbrief.data.FeedRepository
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.QuoteRepository
import com.ohsung.marketbrief.ui.MarketApp
import com.ohsung.marketbrief.ui.MarketTheme
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FeedRepository(app)

    var feed by mutableStateOf<Feed?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Bumped on manual refresh so ticker rows reload their quotes. */
    var refreshKey by mutableIntStateOf(0)
        private set
    var update by mutableStateOf<AppUpdate?>(null)
        private set

    init {
        MolitRepository(app).let { molit ->
            if (molit.apiKey.isBlank() && BuildConfig.MOLIT_KEY.isNotBlank()) molit.apiKey = BuildConfig.MOLIT_KEY
        }
        viewModelScope.launch {
            feed = repo.loadLocal()
            refresh(manual = false)
        }
        viewModelScope.launch { update = UpdateChecker.check(BuildConfig.VERSION_CODE) }
    }

    fun refresh(manual: Boolean = true) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            if (manual) {
                QuoteRepository.clear()
                refreshKey++
            }
            runCatching { repo.fetchRemote() }
                .onSuccess {
                    feed = it
                    error = null
                }
                .onFailure {
                    error = if (feed != null) "인터넷 연결을 확인해 주세요. 저장된 브리핑을 보여드리고 있어요."
                    else "브리핑을 불러오지 못했어요. 아래로 당겨 다시 시도해 주세요."
                }
            refreshing = false
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarketTheme {
                val vm: MainViewModel = viewModel()
                MarketApp(
                    feed = vm.feed,
                    refreshing = vm.refreshing,
                    error = vm.error,
                    refreshKey = vm.refreshKey,
                    update = vm.update,
                    onRefresh = { vm.refresh() },
                    loader = { symbol, range -> QuoteRepository.fetch(symbol, range) },
                )
            }
        }
    }
}
