package io.github.nicolasraoul.rosette.bookmarks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.nicolasraoul.rosette.RosetteApplication
import io.github.nicolasraoul.rosette.databinding.ActivityBookmarksBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val viewModel: BookmarksViewModel by viewModels {
        BookmarksViewModelFactory((application as RosetteApplication).database.bookmarkDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarBookmarks)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Bookmarks"

        val bookmarksAdapter = BookmarksAdapter { bookmark ->
            val resultIntent = Intent()
            resultIntent.putExtra(EXTRA_WIKIDATA_ID, bookmark.wikidataId)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        binding.bookmarksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BookmarksActivity)
            adapter = bookmarksAdapter
        }

        lifecycleScope.launch {
            viewModel.bookmarks.collectLatest { bookmarks ->
                bookmarksAdapter.submitList(bookmarks)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_WIKIDATA_ID = "io.github.nicolasraoul.rosette.WIKIDATA_ID"
    }
}
