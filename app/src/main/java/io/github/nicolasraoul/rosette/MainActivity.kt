package io.github.nicolasraoul.rosette

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.app.Activity
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.github.nicolasraoul.rosette.bookmarks.BookmarksActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var languageManager: LanguageManager
    private lateinit var displayLanguages: Array<String>
    private lateinit var searchPriorityLanguages: Array<String>

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var webviewContainer: LinearLayout
    private val webViews = mutableListOf<WebView>()
    private val progressBarMap = mutableMapOf<WebView, ProgressBar>()

    private lateinit var searchBar: EditText
    private lateinit var statusTextView: TextView

    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionsAdapter: SearchSuggestionsAdapter
    private var searchJob: Job? = null
    private var programmaticTextChange = false
    private var isConfigurationChanging = false
    private var isRestoringFromConfigChange = false

    private var isProgrammaticLoad = false
    private var pagesToLoad = 0
    private var pagesLoaded = 0
    private val currentWikidataId = MutableStateFlow<String?>(null)
    private lateinit var isBookmarked: StateFlow<Boolean>
    private var bookmarkMenuItem: MenuItem? = null
    private var nextRandomLanguageIndex = 0

    private val wikipediaApiService = RetrofitClient.wikipediaApiService
    private val bookmarkDao by lazy {
        (application as RosetteApplication).database.bookmarkDao()
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private val openBookmarksLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val wikidataId = result.data?.getStringExtra(BookmarksActivity.EXTRA_WIKIDATA_ID)
            if (wikidataId != null) {
                Log.d(TAG, "Selected bookmark with Wikidata ID: $wikidataId")
                lifecycleScope.launch {
                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = wikidataId)
                    if (claimsResponse.isSuccessful) {
                        val entity = claimsResponse.body()?.entities?.get(wikidataId)
                        val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                        val label = entity?.labels?.get("en")?.value ?: "Unknown Title"
                    programmaticTextChange = true
                    searchBar.setText(label)
                        if (sitelinks != null) {
                            performFullSearch(label, sitelinks, wikidataId)
                        } else {
                            performFullSearch(label, wikidataId = wikidataId)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to load bookmark", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: Activity starting.")

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize language manager and load configured languages
        languageManager = LanguageManager(this)
        loadConfiguredLanguages()

        mainLayout = findViewById(R.id.main)
        searchBar = findViewById(R.id.search_bar)
        statusTextView = findViewById(R.id.status_text_view)
        webviewContainer = findViewById(R.id.webview_container)
        suggestionsRecyclerView = findViewById(R.id.search_suggestions_recycler_view)

        createWebViews()

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupSuggestions()
        setupSearchTextWatcher()

        isBookmarked = currentWikidataId.flatMapLatest { id ->
            if (id == null) {
                flowOf(false)
            } else {
                bookmarkDao.getBookmark(id).map { it != null }
            }
        }.stateIn(lifecycleScope, SharingStarted.WhileSubscribed(5000), false)

        lifecycleScope.launch {
            isBookmarked.collect { bookmarked ->
                bookmarkMenuItem?.let {
                    val icon = if (bookmarked) R.drawable.ic_star else R.drawable.ic_star_outline
                    it.setIcon(icon)
                }
            }
        }

        // Listen for currentWikidataId changes and save as last visited article
        lifecycleScope.launch {
            currentWikidataId.collect { wikidataId ->
                if (wikidataId != null) {
                    languageManager.saveLastWikidataId(wikidataId)
                }
            }
        }

        if (savedInstanceState != null) {
            Log.d(TAG, "onCreate: Restoring instance state.")
            webViews.forEachIndexed { index, webView ->
                savedInstanceState.getBundle("webView$index")?.let { webView.restoreState(it) }
            }
            savedInstanceState.getString("currentWikidataId")?.let { currentWikidataId.value = it }
            savedInstanceState.getString("searchBarText")?.let { searchBar.setText(it) }
        } else {
            val lastWikidataId = languageManager.getLastWikidataId()
            if (lastWikidataId != null) {
                Log.d(TAG, "onCreate: Found last visited article with Wikidata ID: $lastWikidataId")
                lifecycleScope.launch {
                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = lastWikidataId)
                    if (claimsResponse.isSuccessful) {
                        val entity = claimsResponse.body()?.entities?.get(lastWikidataId)
                        val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                        val label = entity?.labels?.get("en")?.value ?: "Unknown Title"
                        if (sitelinks != null) {
                            performFullSearch(label, sitelinks, lastWikidataId)
                        } else {
                            performFullSearch(label, wikidataId = lastWikidataId)
                        }
                    } else {
                        // Fallback to default if API call fails
                        loadDefaultPages()
                    }
                }
            } else {
                Log.d(TAG, "onCreate: No last visited article found, loading Q8171.")
                lifecycleScope.launch {
                    val wikidataId = "Q8171"
                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = wikidataId)
                    if (claimsResponse.isSuccessful) {
                        val entity = claimsResponse.body()?.entities?.get(wikidataId)
                        val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                        val label = entity?.labels?.get("en")?.value ?: "Q8171"
                        if (sitelinks != null) {
                            performFullSearch(label, sitelinks, wikidataId)
                        } else {
                            performFullSearch(label, wikidataId = wikidataId)
                        }
                    } else {
                        // Fallback to default if API call fails
                        loadDefaultPages()
                    }
                }
            }
        }

        searchBar.setOnFocusChangeListener { view, hasFocus ->
            Log.d(TAG, "SearchBar focus changed: $hasFocus")
            if (!hasFocus && suggestionsRecyclerView.visibility == View.VISIBLE && !isConfigurationChanging && !isRestoringFromConfigChange) {
                Log.d(TAG, "SearchBar lost focus, hiding suggestions")
                suggestionsRecyclerView.visibility = View.GONE
            }
        }

        searchBar.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Disable search on Enter key press - only allow search via dropdown suggestions
                hideKeyboard()
                true
            } else {
                false
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (suggestionsRecyclerView.visibility == View.VISIBLE && !isConfigurationChanging && !isRestoringFromConfigChange) {
                    suggestionsRecyclerView.visibility = View.GONE
                } else if (webViews.any { it.canGoBack() }) {
                    webViews.forEach { if (it.canGoBack()) it.goBack() }
                } else {
                    if (isEnabled) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        Log.d(TAG, "onCreate: Activity creation complete.")

        mainLayout.setOnTouchListener { _, _ ->
            if (suggestionsRecyclerView.visibility == View.VISIBLE && !isConfigurationChanging && !isRestoringFromConfigChange) {
                suggestionsRecyclerView.visibility = View.GONE
                hideKeyboard()
                searchBar.clearFocus()
            }
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        bookmarkMenuItem = menu?.findItem(R.id.action_bookmark)
        // Set initial icon state based on the current value
        val icon = if (isBookmarked.value) R.drawable.ic_star else R.drawable.ic_star_outline
        bookmarkMenuItem?.setIcon(icon)
        
        // Set vertical layout checkbox state
        val verticalLayoutMenuItem = menu?.findItem(R.id.action_vertical_layout)
        verticalLayoutMenuItem?.isChecked = languageManager.isVerticalLayout()
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_bookmark -> {
                Log.d(TAG, "Bookmark action clicked")
                val id = currentWikidataId.value
                if (id != null) {
                    lifecycleScope.launch {
                        if (isBookmarked.value) {
                            bookmarkDao.delete(id)
                            Toast.makeText(this@MainActivity, "Bookmark removed", Toast.LENGTH_SHORT).show()
                        } else {
                            val bookmark = io.github.nicolasraoul.rosette.data.db.Bookmark(
                                wikidataId = id,
                                timestamp = System.currentTimeMillis()
                            )
                            bookmarkDao.insert(bookmark)
                            Toast.makeText(this@MainActivity, "Bookmark saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Cannot save bookmark: article not identified", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_bookmarks -> {
                Log.d(TAG, "Bookmarks action clicked")
                val intent = Intent(this, BookmarksActivity::class.java)
                openBookmarksLauncher.launch(intent)
                true
            }
            R.id.action_settings -> {
                showLanguageSettingsDialog()
                true
            }
            R.id.action_random -> {
                Log.d(TAG, "Random article action clicked")
                lifecycleScope.launch {
                    performRandomArticleSearch()
                }
                true
            }
            R.id.action_home -> {
                Log.d(TAG, "Home action clicked")
                lifecycleScope.launch {
                    val wikidataId = "Q5296" // Item for https://en.wikipedia.org/wiki/Main_Page and its equivalents in other languages
                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = wikidataId)
                    if (claimsResponse.isSuccessful) {
                        val entity = claimsResponse.body()?.entities?.get(wikidataId)
                        val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                        val label = entity?.labels?.get("en")?.value ?: "Home"
                        if (sitelinks != null) {
                            performFullSearch(label, sitelinks, wikidataId)
                        } else {
                            performFullSearch(label, wikidataId = wikidataId)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to load home page", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            R.id.action_vertical_layout -> {
                Log.d(TAG, "Vertical layout action clicked")
                val isCurrentlyVertical = languageManager.isVerticalLayout()
                val newVerticalState = !isCurrentlyVertical
                languageManager.saveVerticalLayout(newVerticalState)
                item.isChecked = newVerticalState
                recreateWebViews() // Recreate WebViews with new orientation
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadConfiguredLanguages() {
        displayLanguages = languageManager.getDisplayLanguages()
        searchPriorityLanguages = languageManager.getSearchPriorityLanguages()
        Log.d(TAG, "Loaded configured languages: display=${displayLanguages.joinToString()}, search=${searchPriorityLanguages.joinToString()}")
    }

    private fun showLanguageSettingsDialog() {
        val dialog = LanguageSettingsDialog.newInstance {
            // Callback when languages are changed
            loadConfiguredLanguages()
            recreateWebViews()
        }
        dialog.show(supportFragmentManager, "LanguageSettingsDialog")
    }

    private fun createWebViews() {
        webviewContainer.removeAllViews()
        webViews.clear()
        progressBarMap.clear()

        // Set container orientation based on preference
        val isVertical = languageManager.isVerticalLayout()
        webviewContainer.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        for (lang in displayLanguages) {
            val frameLayout = FrameLayout(this)
            val layoutParams = if (isVertical) {
                // Vertical layout: equal height distribution
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            } else {
                // Horizontal layout: equal width distribution
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
            }
            frameLayout.layoutParams = layoutParams

            val webView = WebView(this)
            webView.id = View.generateViewId()
            frameLayout.addView(webView)
            webViews.add(webView)

            val progressBar = ProgressBar(this)
            val progressBarLayoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            progressBar.layoutParams = progressBarLayoutParams
            progressBar.visibility = View.GONE
            frameLayout.addView(progressBar)

            progressBarMap[webView] = progressBar
            webviewContainer.addView(frameLayout)

            setupWebView(webView, lang)
        }
    }

    private fun recreateWebViews() {
        createWebViews()

        // Reinitialize suggestions adapter with new language names
        setupSuggestions()

        // Check if we have a current article to preserve
        val currentArticleId = currentWikidataId.value
        if (currentArticleId != null) {
            // Reload the current article to preserve user's reading context
            Log.d(TAG, "recreateWebViews: Preserving current article with Wikidata ID: $currentArticleId")
            lifecycleScope.launch {
                val claimsResponse = wikipediaApiService.getEntityClaims(ids = currentArticleId)
                if (claimsResponse.isSuccessful) {
                    val entity = claimsResponse.body()?.entities?.get(currentArticleId)
                    val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                    val label = entity?.labels?.get("en")?.value ?: searchBar.text.toString().ifEmpty { "Unknown Title" }
                    if (sitelinks != null) {
                        performFullSearch(label, sitelinks, currentArticleId)
                    } else {
                        performFullSearch(label, wikidataId = currentArticleId)
                    }
                } else {
                    // Fallback to loading homepage if API call fails
                    loadHomepageUrls()
                }
            }
        } else {
            // No current article, load homepage URLs as before
            loadHomepageUrls()
        }
    }

    private fun loadHomepageUrls() {
        // Reload initial pages with new languages
        isProgrammaticLoad = true
        pagesToLoad = displayLanguages.size
        webViews.forEachIndexed { index, webView ->
            val lang = displayLanguages[index]
            Log.d(TAG, "loadHomepageUrls: Loading initial URL for $lang WebView.")
            webView.loadUrl(getWikipediaBaseUrl(lang))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: Configuration changed")
        Log.d(TAG, "onConfigurationChanged: Current search text: '${searchBar.text}'")
        Log.d(TAG, "onConfigurationChanged: Search bar has focus: ${searchBar.hasFocus()}")
        Log.d(TAG, "onConfigurationChanged: Suggestions visible: ${suggestionsRecyclerView.visibility == View.VISIBLE}")
        
        // Set flags to prevent search functionality during and after configuration change
        isConfigurationChanging = true
        isRestoringFromConfigChange = true
        
        // Cancel any ongoing search to prevent dropdown from appearing
        searchJob?.cancel()
        
        // Hide search suggestions dropdown when configuration changes (e.g., screen unfolding/folding)
        if (suggestionsRecyclerView.visibility == View.VISIBLE) {
            Log.d(TAG, "onConfigurationChanged: Hiding visible suggestions dropdown")
            suggestionsRecyclerView.visibility = View.GONE
        }
        
        // Clear focus and hide keyboard to prevent triggering search
        if (searchBar.hasFocus()) {
            Log.d(TAG, "onConfigurationChanged: Clearing search bar focus")
            searchBar.clearFocus()
            hideKeyboard()
        }
        
        // Clear search bar text to prevent state restoration from triggering search
        val currentText = searchBar.text.toString()
        if (currentText.isNotEmpty()) {
            Log.d(TAG, "onConfigurationChanged: Clearing search bar text to prevent restoration triggers")
            programmaticTextChange = true
            searchBar.setText("")
        }
        
        // Reset the flags after a longer delay to allow configuration change to complete
        lifecycleScope.launch {
            delay(1500) // Longer delay to account for complex configuration changes like theme switches
            isConfigurationChanging = false
            Log.d(TAG, "onConfigurationChanged: Reset configuration changing flag")
            
            // Additional delay for restoration flag to handle state restoration
            delay(500)
            isRestoringFromConfigChange = false
            Log.d(TAG, "onConfigurationChanged: Reset restoration flag")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViews.forEachIndexed { index, webView ->
            val bundle = Bundle()
            webView.saveState(bundle)
            outState.putBundle("webView$index", bundle)
        }
        outState.putString("currentWikidataId", currentWikidataId.value)
        outState.putString("searchBarText", searchBar.text.toString())
    }

    private fun getDisplayLanguageNames(): List<String> {
        return displayLanguages.map { lang ->
            when (lang) {
                "en" -> "English"
                "fr" -> "French"
                "ja" -> "Japanese"
                "es" -> "Spanish"
                "de" -> "German"
                "it" -> "Italian"
                "pt" -> "Portuguese"
                "ru" -> "Russian"
                "zh" -> "Chinese"
                "ar" -> "Arabic"
                "hi" -> "Hindi"
                "ko" -> "Korean"
                else -> lang.uppercase()
            }
        }
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d(TAG, "onRestoreInstanceState: Restoring instance state")
        
        // Set flag to prevent search functionality during state restoration
        isRestoringFromConfigChange = true
        
        // Reset the flag after a delay to allow state restoration to complete
        lifecycleScope.launch {
            delay(1000) // Give time for state restoration to complete
            isRestoringFromConfigChange = false
            Log.d(TAG, "onRestoreInstanceState: Reset restoration flag")
        }
    }

    private fun setupSuggestions() {
        val displayLanguageNames = getDisplayLanguageNames()
        suggestionsAdapter = SearchSuggestionsAdapter({ suggestion ->
            programmaticTextChange = true
            searchBar.setText(suggestion.label)
            suggestionsRecyclerView.visibility = View.GONE
            hideKeyboard()
            searchBar.clearFocus()
            performSearchFromSuggestion(suggestion)
        }, displayLanguageNames)
        suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionsRecyclerView.adapter = suggestionsAdapter
    }

    private fun setupSearchTextWatcher() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (programmaticTextChange) {
                    Log.d(TAG, "TextWatcher: Ignoring programmatic text change")
                    programmaticTextChange = false
                    return
                }
                
                if (isConfigurationChanging || isRestoringFromConfigChange) {
                    Log.d(TAG, "TextWatcher: Ignoring text change during configuration change or restoration (config: $isConfigurationChanging, restore: $isRestoringFromConfigChange)")
                    return
                }
                
                Log.d(TAG, "TextWatcher: Text changed to: '${s.toString()}', length: ${s.toString().length}")
                searchJob?.cancel()
                val searchText = s.toString().trim()
                if (searchText.length < 2) {
                    Log.d(TAG, "TextWatcher: Text too short, hiding suggestions")
                    suggestionsRecyclerView.visibility = View.GONE
                    return
                }
                Log.d(TAG, "TextWatcher: Starting search job for: '$searchText'")
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    // Double-check the flags in case configuration changed during delay
                    if (isConfigurationChanging || isRestoringFromConfigChange) {
                        Log.d(TAG, "TextWatcher: Configuration changing or restoring during search, aborting (config: $isConfigurationChanging, restore: $isRestoringFromConfigChange)")
                        return@launch
                    }
                    Log.d(TAG, "TextWatcher: Showing suggestions and performing search")
                    suggestionsAdapter.showLoading()
                    suggestionsRecyclerView.visibility = View.VISIBLE
                    performEntitySearch(searchText)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private suspend fun performEntitySearch(searchText: String) {
        try {
            val searchResponse = wikipediaApiService.searchEntities(searchTerm = searchText, language = "en")
            if (searchResponse.isSuccessful) {
                val results = searchResponse.body()?.search
                if (!results.isNullOrEmpty()) {
                    val entityIds = results.joinToString("|") { it.id }
                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = entityIds)
                    if (claimsResponse.isSuccessful) {
                        val entities = claimsResponse.body()?.entities
                        val suggestions = results.mapNotNull { searchResult ->
                            entities?.get(searchResult.id)?.let { entity ->
                                val imageName = (entity.claims?.get("P18")?.firstOrNull()?.mainsnak?.datavalue?.value as? String)?.replace(" ", "_")
                                val imageUrl = if (imageName != null) "https://commons.wikimedia.org/w/thumb.php?f=$imageName&w=100" else null
                                
                                // Check if articles exist in any of the display languages
                                val sitelinks = entity.sitelinks
                                val hasArticleInAnyLanguage = displayLanguages.any { lang ->
                                    sitelinks?.containsKey("${lang}wiki") == true
                                }
                                
                                SearchSuggestion(
                                    searchResult.id, 
                                    searchResult.label, 
                                    searchResult.description ?: "", 
                                    imageUrl,
                                    hasArticleInAnyLanguage = hasArticleInAnyLanguage
                                )
                            }
                        }
                        suggestionsAdapter.updateData(suggestions)
                    } else {
                        suggestionsRecyclerView.visibility = View.GONE
                    }
                } else {
                    suggestionsRecyclerView.visibility = View.GONE
                }
            } else {
                suggestionsRecyclerView.visibility = View.GONE
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Entity search failed", e)
            suggestionsRecyclerView.visibility = View.GONE
        }
    }

    private fun performSearchFromSuggestion(suggestion: SearchSuggestion) {
        lifecycleScope.launch {
            val claimsResponse = wikipediaApiService.getEntityClaims(ids = suggestion.id)
            if (claimsResponse.isSuccessful) {
                val entity = claimsResponse.body()?.entities?.get(suggestion.id)
                val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                if (sitelinks != null) {
                    val primaryTitle = sitelinks["enwiki"] ?: sitelinks.values.firstOrNull() ?: suggestion.label
                    performFullSearch(primaryTitle, sitelinks, suggestion.id)
                } else {
                    performFullSearch(suggestion.label)
                }
            } else {
                performFullSearch(suggestion.label)
            }
        }
    }

    private fun getArticleTitleFromUrl(url: Uri): String? {
        var articleTitle: String? = null
        if (url.path?.startsWith("/wiki/") == true) {
            val pathSegments = url.pathSegments
            if (pathSegments.size > 1 && pathSegments[0] == "wiki") {
                articleTitle = URLDecoder.decode(pathSegments.subList(1, pathSegments.size).joinToString("/"), "UTF-8").replace("_", " ")
            }
        } else if (url.path?.startsWith("/w/index.php") == true) {
            articleTitle = url.getQueryParameter("title")?.replace("_", " ")
        }
        return articleTitle
    }

    private inner class TrilingualWebViewClient(private val webViewIdentifier: String) : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "onPageStarted ($webViewIdentifier): Loading URL: $url")
            progressBarMap[view]?.visibility = View.VISIBLE
            view?.visibility = View.INVISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "onPageFinished ($webViewIdentifier): Finished loading URL: $url")

            // Update search bar text on back/forward navigation
            if (!isProgrammaticLoad) {
                url?.let { Uri.parse(it) }?.let { uri ->
                    getArticleTitleFromUrl(uri)?.let { articleTitle ->
                        programmaticTextChange = true
                        searchBar.setText(articleTitle)
                    }
                }
            }

            // Inject CSS to add padding to prevent text cropping at edges
            view?.evaluateJavascript("""
                (function() {
                    var style = document.createElement('style');
                    style.textContent = 
                        'body { padding-left: 8px !important; padding-right: 8px !important; } ' +
                        '.mw-parser-output { padding-left: 8px !important; padding-right: 8px !important; } ' +
                        '#content { padding-left: 8px !important; padding-right: 8px !important; }';
                    document.head.appendChild(style);
                })();
            """, null)
            
            progressBarMap[view]?.visibility = View.GONE
            view?.visibility = View.VISIBLE
            if (isProgrammaticLoad) {
                pagesLoaded++
                checkAllWebViewsLoaded()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            Log.d(TAG, "shouldOverrideUrlLoading ($webViewIdentifier): Intercepted URL: $url. Programmatic load: $isProgrammaticLoad")
            if (isProgrammaticLoad) return false

            if (url.host?.endsWith("wikipedia.org") == true) {
                val articleTitle = getArticleTitleFromUrl(url)
                if (!articleTitle.isNullOrEmpty()) {
                    programmaticTextChange = true
                    searchBar.setText(articleTitle)

                    lifecycleScope.launch {
                        val wikidataId = getWikidataIdForTitle(webViewIdentifier, articleTitle)
                        if (wikidataId != null) {
                            val claimsResponse = wikipediaApiService.getEntityClaims(ids = wikidataId)
                            if (claimsResponse.isSuccessful) {
                                val entity = claimsResponse.body()?.entities?.get(wikidataId)
                                val sitelinks = entity?.sitelinks?.mapValues { it.value.title }
                                if (sitelinks != null) {
                                    performFullSearch(articleTitle, sitelinks, wikidataId)
                                } else {
                                    performFullSearch(articleTitle)
                                }
                            } else {
                                performFullSearch(articleTitle)
                            }
                        } else {
                            performFullSearch(articleTitle)
                        }
                    }
                    return true
                }
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open external link", e)
                }
            }
            return false
        }
    }

    private suspend fun getWikidataIdForTitle(lang: String, title: String): String? {
        val baseUrlForApi = "https://$lang.wikipedia.org/w/api.php"
        try {
            val response = wikipediaApiService.getWikidataId(baseUrl = baseUrlForApi, titles = title)
            if (response.isSuccessful) {
                return response.body()?.query?.pages?.values?.firstOrNull()?.pageprops?.wikibaseItem
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "getWikidataIdForTitle failed for $title on $lang", e)
        }
        return null
    }

    private fun checkAllWebViewsLoaded() {
        if (pagesLoaded >= pagesToLoad) {
            isProgrammaticLoad = false
            pagesLoaded = 0
            statusTextView.visibility = View.GONE
            Log.d(TAG, "checkAllWebViewsLoaded: All programmatic loads are complete.")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWebView(webView: WebView, lang: String) {
        Log.d(TAG, "setupWebView: Setting up $lang WebView.")
        webView.webViewClient = TrilingualWebViewClient(lang)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.textZoom = 100
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun updateStatus(message: String) {
        statusTextView.visibility = View.VISIBLE
        statusTextView.text = message
        Log.d(TAG, "Status: $message")
    }

    private fun getWikipediaBaseUrl(lang: String): String = "https://$lang.m.wikipedia.org"
    private fun getWikipediaPageUrl(lang: String, title: String): String = "https://$lang.m.wikipedia.org/wiki/${title.replace(" ", "_")}"

    private fun loadDefaultPages() {
        isProgrammaticLoad = true
        pagesToLoad = displayLanguages.size
        webViews.forEachIndexed { index, webView ->
            val lang = displayLanguages[index]
            Log.d(TAG, "loadDefaultPages: Loading initial URL for $lang WebView.")
            webView.loadUrl(getWikipediaBaseUrl(lang))
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private suspend fun getFinalArticleTitle(lang: String, searchTerm: String): String? {
        val baseUrlForApi = "https://$lang.wikipedia.org/w/api.php"
        val formattedSearchTerm = searchTerm.replace(" ", "_")
        try {
            val response = wikipediaApiService.getArticleInfo(baseUrl = baseUrlForApi, titles = formattedSearchTerm)
            if (response.isSuccessful) {
                return response.body()?.query?.pages?.values?.firstOrNull { it.title != null }?.title
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "getFinalArticleTitle failed for $searchTerm on $lang", e)
        }
        return null
    }

    private suspend fun getLangLinksForTitle(lang: String, articleTitle: String): Map<String, String>? {
        val baseUrlForApi = "https://$lang.wikipedia.org/w/api.php"
        try {
            val response = wikipediaApiService.getLanguageLinks(baseUrl = baseUrlForApi, titles = articleTitle)
            if (response.isSuccessful) {
                return response.body()?.query?.pages?.values?.firstOrNull()?.langlinks?.associate { it.lang to it.title }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "getLangLinksForTitle failed for $articleTitle on $lang", e)
        }
        return null
    }

    private fun showMissingArticleMessage(webView: WebView?, lang: String) {
        val message = "The $lang Wikipedia is waiting for someone to write an article on that topic."
        val htmlContent = """
            <html>
                <body>
                    <p>$message</p>
                </body>
            </html>
        """.trimIndent()
        webView?.loadData(htmlContent, "text/html; charset=utf-8", "UTF-8")
    }

    private fun performFullSearch(searchTerm: String, sitelinks: Map<String, String>? = null, wikidataId: String? = null) {
        Log.d(TAG, "performFullSearch: Starting a new search for '$searchTerm'. Wikidata ID: $wikidataId")
        this.currentWikidataId.value = wikidataId
        // TODO: Refactor the 'else' block below to fetch the wikidataId for plain searches to allow bookmarking.

        lifecycleScope.launch {
            isProgrammaticLoad = true
            pagesToLoad = 0
            pagesLoaded = 0

            progressBarMap.values.forEach { it.visibility = View.VISIBLE }
            webViews.forEach { it.visibility = View.INVISIBLE }

            if (sitelinks != null) {
                updateStatus("Loading articles for \"$searchTerm\"...")
                webViews.forEachIndexed { index, webView ->
                    val lang = displayLanguages[index]
                    val siteKey = "${lang}wiki"
                    val title = sitelinks[siteKey]
                    if (title != null) {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch (with sitelinks): Loading '$title' in $lang WebView.")
                        webView.loadUrl(getWikipediaPageUrl(lang, title))
                    } else {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch (with sitelinks): No translation for $lang, showing message.")
                        showMissingArticleMessage(webView, lang)
                    }
                }
            } else {
                var sourceLangFound: String? = null
                var finalTitleFromSource: String? = null

                for (lang in searchPriorityLanguages) {
                    finalTitleFromSource = getFinalArticleTitle(lang, searchTerm)
                    if (finalTitleFromSource != null) {
                        sourceLangFound = lang
                        break
                    }
                }

                if (sourceLangFound == null || finalTitleFromSource == null) {
                    updateStatus("Article \"$searchTerm\" not found.")
                    this@MainActivity.currentWikidataId.value = null
                    progressBarMap.values.forEach { it.visibility = View.GONE }
                    isProgrammaticLoad = false
                    return@launch
                }

                updateStatus("Found \"$finalTitleFromSource\" on $sourceLangFound.wikipedia.org. Fetching translations...")
                val langLinksMap = getLangLinksForTitle(sourceLangFound, finalTitleFromSource)

                updateStatus("Loading articles...")
                webViews.forEachIndexed { index, webView ->
                    val lang = displayLanguages[index]
                    val title = if (lang == sourceLangFound) finalTitleFromSource else langLinksMap?.get(lang)
                    if (title != null) {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch: Loading '$title' in $lang WebView.")
                        webView.loadUrl(getWikipediaPageUrl(lang, title))
                    } else {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch (no sitelinks): No translation for $lang, showing message.")
                        showMissingArticleMessage(webView, lang)
                    }
                }
            }

            if(pagesToLoad == 0) {
                isProgrammaticLoad = false
                checkAllWebViewsLoaded()
            }
        }
    }

    private suspend fun performRandomArticleSearch() {
        updateStatus(getString(R.string.loading_random_article))
        val sortedLangs = languageManager.getDisplayLanguagesSortedByArticleCount()
        val maxAttemptsPerLang = 10 // Try 10 times per language before moving to the next

        for (lang in sortedLangs) {
            Log.d(TAG, "Searching for a random article on the '$lang' Wikipedia...")
            repeat(maxAttemptsPerLang) { attempt ->
                Log.d(TAG, "Attempt ${attempt + 1}/$maxAttemptsPerLang on $lang.wikipedia.org")
                try {
                    val baseUrlForApi = "https://$lang.wikipedia.org/w/api.php"
                    val randomResponse = wikipediaApiService.getRandomWikipediaArticles(baseUrl = baseUrlForApi)
                    if (!randomResponse.isSuccessful) {
                        Log.e(TAG, "Failed to get random article from $lang.wikipedia.org: ${randomResponse.code()}")
                        if (randomResponse.code() == 429) {
                            Log.w(TAG, "Got HTTP 429, waiting 5 seconds.")
                            delay(5000)
                        }
                        return@repeat // Continue to next attempt
                    }

                    val randomArticle = randomResponse.body()?.query?.random?.firstOrNull()
                    if (randomArticle == null) {
                        Log.w(TAG, "No random article returned from $lang.wikipedia.org")
                        return@repeat // Continue to next attempt
                    }

                    Log.d(TAG, "Checking random article: ${randomArticle.title} from $lang.wikipedia.org")

                    val wikidataId = getWikidataIdForTitle(lang, randomArticle.title)
                    if (wikidataId == null) {
                        Log.d(TAG, "No Wikidata ID found for ${randomArticle.title}")
                        return@repeat // Continue to next attempt
                    }

                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = wikidataId)
                    if (!claimsResponse.isSuccessful) {
                        Log.d(TAG, "Failed to get entity claims for $wikidataId")
                        return@repeat // Continue to next attempt
                    }

                    val entity = claimsResponse.body()?.entities?.get(wikidataId)
                    val sitelinks = entity?.sitelinks?.mapValues { it.value.title }

                    if (sitelinks == null) {
                        Log.d(TAG, "No sitelinks found for $wikidataId")
                        return@repeat // Continue to next attempt
                    }

                    val hasAllLanguages = displayLanguages.all { displayLang ->
                        sitelinks.containsKey("${displayLang}wiki")
                    }

                    if (hasAllLanguages) {
                        Log.d(TAG, "Found suitable random article: ${randomArticle.title} (Wikidata: $wikidataId)")
                        val label = entity.labels?.get("en")?.value ?: randomArticle.title

                        programmaticTextChange = true
                        searchBar.setText("")
                        suggestionsRecyclerView.visibility = View.GONE
                        hideKeyboard()
                        searchBar.clearFocus()

                        performFullSearch(label, sitelinks, wikidataId)
                        return // Exit the whole function on success
                    } else {
                        Log.d(TAG, "Article ${randomArticle.title} doesn't have translations in all required languages.")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error during random article search on $lang.wikipedia.org", e)
                }
                delay(1000) // Small delay between attempts
            }
        }

        Log.w(TAG, "Could not find a suitable random article after checking all configured languages.")
        updateStatus(getString(R.string.random_article_not_found))
        lifecycleScope.launch {
            delay(3000)
            statusTextView.visibility = View.GONE
        }
    }
}

data class SearchSuggestion(
    val id: String,
    val label: String,
    val description: String,
    val thumbnailUrl: String?,
    val isLoader: Boolean = false,
    val hasArticleInAnyLanguage: Boolean = true
)

class SearchSuggestionsAdapter(
    private val onClick: (SearchSuggestion) -> Unit,
    private val displayLanguageNames: List<String>
) : RecyclerView.Adapter<SearchSuggestionsAdapter.ViewHolder>() {

    private var suggestions = mutableListOf<SearchSuggestion>()

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSuggestions: List<SearchSuggestion>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun showLoading() {
        suggestions.clear()
        suggestions.add(SearchSuggestion("", "", "", null, isLoader = true))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.search_suggestion_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.suggestion_title)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.suggestion_description)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.suggestion_thumbnail)
        private val loadingIndicator: View = itemView.findViewById(R.id.loading_indicator)
        private val contentGroup: Group = itemView.findViewById(R.id.suggestion_content_group)

        fun bind(suggestion: SearchSuggestion) {
            if (suggestion.isLoader) {
                contentGroup.visibility = View.GONE
                loadingIndicator.visibility = View.VISIBLE
            } else {
                contentGroup.visibility = View.VISIBLE
                loadingIndicator.visibility = View.GONE
                titleTextView.text = suggestion.label
                descriptionTextView.text = suggestion.description
                
                // Apply greyed-out styling if no articles available
                val alpha = if (suggestion.hasArticleInAnyLanguage) 1.0f else 0.5f
                titleTextView.alpha = alpha
                descriptionTextView.alpha = alpha
                thumbnailImageView.alpha = alpha
                
                itemView.setOnClickListener { 
                    if (suggestion.hasArticleInAnyLanguage) {
                        onClick(suggestion)
                    } else {
                        // Show toast for unavailable items
                        val context = itemView.context
                        val message = "This item does not yet have an article in ${displayLanguageNames.joinToString(", ")}"
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                if (suggestion.thumbnailUrl != null) {
                    Glide.with(itemView.context)
                        .load(suggestion.thumbnailUrl)
                        .centerCrop()
                        .into(thumbnailImageView)
                } else {
                    thumbnailImageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
    }
}
