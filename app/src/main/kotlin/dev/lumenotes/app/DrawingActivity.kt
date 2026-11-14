package dev.lumenotes.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.lumenotes.app.databinding.ActivityDrawingBinding
import dev.lumenotes.data.LatestSaveQueue
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DrawingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDrawingBinding
    private lateinit var viewModel: DrawingViewModel
    private var leaving = false
    private var documentApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeInsets(window, binding.root)
        binding.drawingToolbar.setNavigationButtonAsBack()
        viewModel = ViewModelProvider(this)[DrawingViewModel::class.java]
        binding.inkCanvas.isEnabled = false
        setupTools()
        observeState()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAfterSave()
                }
            }
        )
    }

    private fun setupTools() {
        binding.inkCanvas.onDocumentChanged = viewModel::update
        binding.inkPen.setOnClickListener {
            binding.inkCanvas.eraserSelected = false
            updateToolSelection()
        }
        binding.inkEraser.setOnClickListener {
            binding.inkCanvas.eraserSelected = true
            updateToolSelection()
        }
        binding.inkUndo.setOnClickListener { binding.inkCanvas.undoLastStroke() }
        binding.inkClear.setOnClickListener {
            AlertDialog.Builder(this).setMessage(R.string.clear_drawing_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_drawing) { _, _ -> binding.inkCanvas.clearDocument() }
                .show()
        }
        binding.inkBlack.setOnClickListener { selectColor(Color.rgb(31, 36, 52)) }
        binding.inkBlue.setOnClickListener { selectColor(Color.rgb(48, 84, 190)) }
        binding.inkRed.setOnClickListener { selectColor(Color.rgb(180, 53, 57)) }
        binding.stylusOnly.setOnCheckedChangeListener { _, checked -> binding.inkCanvas.stylusOnly = checked }
        binding.drawingStatus.setOnClickListener { viewModel.retrySave() }
        updateToolSelection()
    }

    private fun selectColor(color: Int) {
        binding.inkCanvas.inkColor = color
        binding.inkCanvas.eraserSelected = false
        updateToolSelection()
    }

    private fun updateToolSelection() {
        binding.inkPen.alpha = if (binding.inkCanvas.eraserSelected) 0.55f else 1f
        binding.inkEraser.alpha = if (binding.inkCanvas.eraserSelected) 1f else 0.55f
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        when (state) {
                            DrawingState.Loading -> binding.inkCanvas.isEnabled = false

                            DrawingState.Error -> {
                                binding.inkCanvas.isEnabled = false
                                Toast.makeText(this@DrawingActivity, R.string.drawing_corrupt, Toast.LENGTH_LONG).show()
                                leaving = true
                                finish()
                            }

                            is DrawingState.Ready -> {
                                if (!documentApplied) {
                                    binding.inkCanvas.setDocument(state.strokes)
                                    documentApplied = true
                                }
                                binding.inkCanvas.isEnabled = true
                            }
                        }
                    }
                }
                launch {
                    viewModel.saveStatus.collect { phase ->
                        binding.drawingStatus.setText(
                            when (phase) {
                                LatestSaveQueue.Phase.SAVING -> R.string.saving
                                LatestSaveQueue.Phase.FAILED -> R.string.save_failed
                                else -> R.string.saved
                            }
                        )
                    }
                }
            }
        }
    }

    private fun finishAfterSave() {
        if (leaving) return
        if (viewModel.state.value !is DrawingState.Ready) {
            leaving = true
            finish()
            return
        }
        leaving = true
        lifecycleScope.launch {
            if (viewModel.flush()) {
                finish()
            } else {
                leaving = false
                AlertDialog.Builder(this@DrawingActivity)
                    .setTitle(R.string.leave_unsaved_title)
                    .setMessage(R.string.leave_unsaved_message)
                    .setNegativeButton(R.string.keep_editing, null)
                    .setPositiveButton(R.string.leave_unsaved) { _, _ ->
                        leaving = true
                        finish()
                    }
                    .show()
            }
        }
    }
}
