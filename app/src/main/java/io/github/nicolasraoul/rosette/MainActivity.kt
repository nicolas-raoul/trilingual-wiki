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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var languageManager: LanguageManager
    private lateinit var displayLanguages: Array<String>
    private lateinit var searchPriorityLanguages: Array<String>

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var webViewEN: WebView
    private lateinit var webViewFR: WebView
    private lateinit var webViewJA: WebView
    private lateinit var webViewMap: Map<String, WebView>

    private lateinit var progressBarEN: ProgressBar
    private lateinit var progressBarFR: ProgressBar
    private lateinit var progressBarJA: ProgressBar
    private lateinit var progressBarMap: Map<WebView, ProgressBar>

    private lateinit var searchBar: EditText
    private lateinit var statusTextView: TextView

    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionsAdapter: SearchSuggestionsAdapter
    private var searchJob: Job? = null
    private var programmaticTextChange = false

    private var isProgrammaticLoad = false
    private var pagesToLoad = 0
    private var pagesLoaded = 0

    private val wikipediaApiService = RetrofitClient.wikipediaApiService

    companion object {
        private const val TAG = "MainActivity"
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

        webViewEN = findViewById(R.id.webViewEN)
        webViewFR = findViewById(R.id.webViewFR)
        webViewJA = findViewById(R.id.webViewJA)

        progressBarEN = findViewById(R.id.progressBarEN)
        progressBarFR = findViewById(R.id.progressBarFR)
        progressBarJA = findViewById(R.id.progressBarJA)

        suggestionsRecyclerView = findViewById(R.id.search_suggestions_recycler_view)

        webViewMap = mapOf(
            displayLanguages[0] to webViewEN,
            displayLanguages[1] to webViewFR,
            displayLanguages[2] to webViewJA
        )
        progressBarMap = mapOf(
            webViewEN to progressBarEN,
            webViewFR to progressBarFR,
            webViewJA to progressBarJA
        )

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        webViewMap.forEach { (lang, webView) -> setupWebView(webView, lang) }

        setupSuggestions()

        if (savedInstanceState != null) {
            Log.d(TAG, "onCreate: Restoring instance state.")
            webViewMap.forEach { (key, webView) ->
                savedInstanceState.getBundle("webView$key")?.let { webView.restoreState(it) }
            }
        } else {
            Log.d(TAG, "onCreate: No saved instance state, loading initial URLs.")
            isProgrammaticLoad = true
            pagesToLoad = displayLanguages.size
            webViewMap.forEach { (lang, webView) ->
                Log.d(TAG, "onCreate: Loading initial URL for $lang WebView.")
                webView.loadUrl(getWikipediaBaseUrl(lang))
            }
        }

        searchBar.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val searchTerm = textView.text.toString().trim()
                if (searchTerm.isNotEmpty()) {
                    hideKeyboard()
                    suggestionsRecyclerView.visibility = View.GONE
                    performFullSearch(searchTerm)
                }
                true
            } else {
                false
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (suggestionsRecyclerView.visibility == View.VISIBLE) {
                    suggestionsRecyclerView.visibility = View.GONE
                } else if (webViewMap.values.any { it.canGoBack() }) {
                    webViewMap.values.forEach { if (it.canGoBack()) it.goBack() }
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
            if (suggestionsRecyclerView.visibility == View.VISIBLE) {
                suggestionsRecyclerView.visibility = View.GONE
                hideKeyboard()
                searchBar.clearFocus()
            }
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showLanguageSettingsDialog()
                true
            }
            R.id.action_random -> {
                performRandomArticleSearch()
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

    private fun recreateWebViews() {
        // Recreate the webview mapping with new languages
        webViewMap = mapOf(
            displayLanguages[0] to webViewEN,
            displayLanguages[1] to webViewFR,
            displayLanguages[2] to webViewJA
        )
        
        // Reload initial pages with new languages
        isProgrammaticLoad = true
        pagesToLoad = displayLanguages.size
        webViewMap.forEach { (lang, webView) ->
            Log.d(TAG, "recreateWebViews: Loading initial URL for $lang WebView.")
            webView.loadUrl(getWikipediaBaseUrl(lang))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViewMap.forEach { (key, webView) ->
            val bundle = Bundle()
            webView.saveState(bundle)
            outState.putBundle("webView$key", bundle)
        }
    }

    private fun setupSuggestions() {
        suggestionsAdapter = SearchSuggestionsAdapter { suggestion ->
            programmaticTextChange = true
            searchBar.setText(suggestion.label)
            suggestionsRecyclerView.visibility = View.GONE
            hideKeyboard()
            searchBar.clearFocus()
            performSearchFromSuggestion(suggestion)
        }
        suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionsRecyclerView.adapter = suggestionsAdapter

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (programmaticTextChange) {
                    programmaticTextChange = false
                    return
                }
                searchJob?.cancel()
                val searchText = s.toString().trim()
                if (searchText.length < 2) {
                    suggestionsRecyclerView.visibility = View.GONE
                    return
                }
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
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
                                SearchSuggestion(searchResult.id, searchResult.label, searchResult.description ?: "", imageUrl)
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
                    performFullSearch(primaryTitle, sitelinks)
                } else {
                    performFullSearch(suggestion.label)
                }
            } else {
                performFullSearch(suggestion.label)
            }
        }
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
                var articleTitle: String? = null
                if (url.path?.startsWith("/wiki/") == true) {
                    val pathSegments = url.pathSegments
                    if (pathSegments.size > 1 && pathSegments[0] == "wiki") {
                        articleTitle = URLDecoder.decode(pathSegments.subList(1, pathSegments.size).joinToString("/"), "UTF-8").replace("_", " ")
                    }
                } else if (url.path?.startsWith("/w/index.php") == true) {
                    articleTitle = url.getQueryParameter("title")?.replace("_", " ")
                }

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
                                    performFullSearch(articleTitle, sitelinks)
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

    private fun performFullSearch(searchTerm: String, sitelinks: Map<String, String>? = null) {
        Log.d(TAG, "performFullSearch: Starting a new search for '$searchTerm'.")
        lifecycleScope.launch {
            isProgrammaticLoad = true
            pagesToLoad = 0
            pagesLoaded = 0

            progressBarMap.values.forEach { it.visibility = View.VISIBLE }
            webViewMap.values.forEach { it.visibility = View.INVISIBLE }

            if (sitelinks != null) {
                updateStatus("Loading articles for \"$searchTerm\"...")
                displayLanguages.forEach { lang ->
                    val webView = webViewMap[lang]
                    val siteKey = "${lang}wiki"
                    val title = sitelinks[siteKey]
                    if (title != null) {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch (with sitelinks): Loading '$title' in $lang WebView.")
                        webView?.loadUrl(getWikipediaPageUrl(lang, title))
                    } else {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch (with sitelinks): No translation for $lang, loading search page.")
                        webView?.loadUrl(getWikipediaBaseUrl(lang) + "/w/index.php?title=Special:Search&search=${searchTerm.replace(" ", "_")}")
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
                    progressBarMap.values.forEach { it.visibility = View.GONE }
                    isProgrammaticLoad = false
                    return@launch
                }

                updateStatus("Found \"$finalTitleFromSource\" on $sourceLangFound.wikipedia.org. Fetching translations...")
                val langLinksMap = getLangLinksForTitle(sourceLangFound, finalTitleFromSource)

                updateStatus("Loading articles...")
                displayLanguages.forEach { lang ->
                    val webView = webViewMap[lang]
                    val title = if (lang == sourceLangFound) finalTitleFromSource else langLinksMap?.get(lang)
                    if (title != null) {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch: Loading '$title' in $lang WebView.")
                        webView?.loadUrl(getWikipediaPageUrl(lang, title))
                    } else {
                        pagesToLoad++
                        Log.d(TAG, "performFullSearch: No translation for $lang, loading search page.")
                        webView?.loadUrl(getWikipediaBaseUrl(lang) + "/w/index.php?title=Special:Search&search=${finalTitleFromSource.replace(" ", "_")}")
                    }
                }
            }

            if(pagesToLoad == 0) {
                isProgrammaticLoad = false
                checkAllWebViewsLoaded()
            }
        }
    }

    private fun performRandomArticleSearch() {
        Log.d(TAG, "performRandomArticleSearch: Starting random article search.")
        lifecycleScope.launch {
            hideKeyboard()
            suggestionsRecyclerView.visibility = View.GONE
            searchBar.clearFocus()
            
            updateStatus(getString(R.string.finding_random_article))
            
            var attempts = 0
            val maxAttempts = 50 // Try up to 50 random items
            
            while (attempts < maxAttempts) {
                attempts++
                Log.d(TAG, "performRandomArticleSearch: Attempt $attempts")
                
                try {
                    // Generate random Q-IDs to try (Wikidata has millions of items)
                    val randomQIds = generateRandomQIds(5) // Try 5 at a time
                    val qIdsString = randomQIds.joinToString("|")
                    
                    Log.d(TAG, "performRandomArticleSearch: Trying Q-IDs: $qIdsString")
                    
                    // Get entity data for these Q-IDs
                    val claimsResponse = wikipediaApiService.getEntityClaims(ids = qIdsString)
                    
                    if (claimsResponse.isSuccessful) {
                        val entities = claimsResponse.body()?.entities
                        
                        if (!entities.isNullOrEmpty()) {
                            for ((qId, entity) in entities) {
                                // Skip if this entity was returned as missing
                                if (entity.sitelinks.isNullOrEmpty()) continue
                                
                                val sitelinks = entity.sitelinks.mapValues { it.value.title }
                                
                                // Check if we have articles in all 3 selected languages
                                val requiredSiteKeys = displayLanguages.map { "${it}wiki" }
                                val availableSiteKeys = sitelinks.keys
                                
                                if (requiredSiteKeys.all { it in availableSiteKeys }) {
                                    // Found a suitable article!
                                    Log.d(TAG, "performRandomArticleSearch: Found suitable article: $qId")
                                    val primaryTitle = sitelinks["enwiki"] ?: sitelinks[requiredSiteKeys.first()] ?: sitelinks.values.first()
                                    programmaticTextChange = true
                                    searchBar.setText(primaryTitle)
                                    performFullSearch(primaryTitle, sitelinks)
                                    return@launch
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error during random article search attempt $attempts", e)
                }
            }
            
            // If we get here, we didn't find a suitable article
            Log.d(TAG, "performRandomArticleSearch: No suitable article found after $maxAttempts attempts")
            updateStatus(getString(R.string.no_random_article_found))
        }
    }
    
    private fun generateRandomQIds(count: Int): List<String> {
        // Generate random Q-IDs within a reasonable range
        // Wikidata has items up to around Q100M+, but most active items are in lower ranges
        // We'll focus on Q1 to Q10M for better hit rate
        val random = kotlin.random.Random
        return (1..count).map { 
            "Q${random.nextInt(1, 10_000_000)}"
        }
    }
}

data class SearchSuggestion(
    val id: String,
    val label: String,
    val description: String,
    val thumbnailUrl: String?,
    val isLoader: Boolean = false
)

class SearchSuggestionsAdapter(
    private val onClick: (SearchSuggestion) -> Unit
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
                itemView.setOnClickListener { onClick(suggestion) }

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