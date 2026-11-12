package com.bazdid.trans.ui.project

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bazdid.trans.data.entity.Project
import com.bazdid.trans.databinding.ItemProjectBinding
import java.text.SimpleDateFormat
import java.util.*

class ProjectAdapter(
    private val onClick: (Project) -> Unit,
    private val onLongClick: (Project) -> Unit
) : ListAdapter<Project, ProjectAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("fa"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(project: Project) {
            binding.tvProjectName.text = project.name
            binding.tvProjectDesc.text = project.description.ifEmpty { "بدون توضیح" }
            binding.tvProjectDate.text = dateFormat.format(Date(project.updatedAt))

            binding.root.setOnClickListener { onClick(project) }
            binding.root.setOnLongClickListener {
                onLongClick(project)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(old: Project, new: Project) = old.id == new.id
        override fun areContentsTheSame(old: Project, new: Project) = old == new
    }
}
