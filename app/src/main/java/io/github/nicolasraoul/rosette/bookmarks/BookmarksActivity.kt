package io.github.nicolasraoul.rosette.bookmarks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.github.nicolasraoul.rosette.LanguageManager
import io.github.nicolasraoul.rosette.RosetteApplication
import io.github.nicolasraoul.rosette.databinding.ActivityBookmarksBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val viewModel: BookmarksViewModel by viewModels {
        BookmarksViewModelFactory(
            (application as RosetteApplication).database.bookmarkDao(),
            LanguageManager(this)
        )
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

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val bookmark = bookmarksAdapter.currentList[position]
                viewModel.delete(bookmark)
                Snackbar.make(binding.root, "Bookmark deleted", Snackbar.LENGTH_LONG).show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.bookmarksRecyclerView)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_WIKIDATA_ID = "io.github.nicolasraoul.rosette.WIKIDATA_ID"
    }
}
