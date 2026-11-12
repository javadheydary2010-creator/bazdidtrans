package com.bazdid.trans.ui.transformer

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bazdid.trans.data.entity.Feeder
import com.bazdid.trans.databinding.ItemFeederBinding
import com.bazdid.trans.util.LoadCalculator

class FeederAdapter(
    private val onCountChanged: (Feeder, Int, Int) -> Unit,
    private val onDelete: (Feeder) -> Unit
) : ListAdapter<Feeder, FeederAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeederBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFeederBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentFeeder: Feeder? = null
        private var singleWatcher: TextWatcher? = null
        private var threeWatcher: TextWatcher? = null

        fun bind(feeder: Feeder) {
            currentFeeder = feeder

            // Remove previous watchers to avoid loops
            singleWatcher?.let { binding.etSinglePhase.removeTextChangedListener(it) }
            threeWatcher?.let { binding.etThreePhase.removeTextChangedListener(it) }

            binding.tvFeederName.text = feeder.name.ifEmpty { "فیدر" }
            binding.tvFeederLoad.text = LoadCalculator.formatLoad(feeder.calculateLoadKw())
            binding.etSinglePhase.setText(feeder.singlePhaseCount.toString())
            binding.etThreePhase.setText(feeder.threePhaseCount.toString())

            singleWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val single = s.toString().toIntOrNull() ?: 0
                    val three = binding.etThreePhase.text.toString().toIntOrNull() ?: 0
                    currentFeeder?.let { onCountChanged(it, single, three) }
                }
            }

            threeWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val three = s.toString().toIntOrNull() ?: 0
                    val single = binding.etSinglePhase.text.toString().toIntOrNull() ?: 0
                    currentFeeder?.let { onCountChanged(it, single, three) }
                }
            }

            binding.etSinglePhase.addTextChangedListener(singleWatcher)
            binding.etThreePhase.addTextChangedListener(threeWatcher)

            binding.btnDeleteFeeder.setOnClickListener {
                currentFeeder?.let { onDelete(it) }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Feeder>() {
        override fun areItemsTheSame(old: Feeder, new: Feeder) = old.id == new.id
        override fun areContentsTheSame(old: Feeder, new: Feeder) = old == new
    }
}
