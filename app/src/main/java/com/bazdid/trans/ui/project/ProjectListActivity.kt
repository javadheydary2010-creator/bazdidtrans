package com.bazdid.trans.ui.project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bazdid.trans.R
import com.bazdid.trans.data.entity.Project
import com.bazdid.trans.databinding.ActivityProjectListBinding
import com.bazdid.trans.ui.map.MapActivity
import com.bazdid.trans.viewmodel.ProjectViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProjectListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectListBinding
    private val viewModel: ProjectViewModel by viewModels()
    private lateinit var adapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = ProjectAdapter(
            onClick = { project -> openProject(project) },
            onLongClick = { project -> showDeleteDialog(project) }
        )

        binding.recyclerProjects.layoutManager = LinearLayoutManager(this)
        binding.recyclerProjects.adapter = adapter

        binding.fabNewProject.setOnClickListener { showNewProjectDialog() }

        lifecycleScope.launch {
            viewModel.projects.collectLatest { list ->
                adapter.submitList(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun showNewProjectDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_project, null)
        val etName = view.findViewById<EditText>(R.id.etProjectName)
        val etDesc = view.findViewById<EditText>(R.id.etProjectDesc)

        AlertDialog.Builder(this)
            .setTitle(R.string.new_project)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "نام پروژه الزامی است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val desc = etDesc.text.toString().trim()
                viewModel.createProject(name, desc)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openProject(project: Project) {
        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_PROJECT_ID, project.id)
            putExtra(MapActivity.EXTRA_PROJECT_NAME, project.name)
        }
        startActivity(intent)
    }

    private fun showDeleteDialog(project: Project) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage("پروژه «${project.name}» حذف شود؟")
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.deleteProject(project)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
