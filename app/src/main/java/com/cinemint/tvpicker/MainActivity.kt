package com.cinemint.tvpicker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var pathTextView: TextView
    private lateinit var btnUp: View
    private lateinit var btnHome: View

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val adapter = FileAdapter { file -> onFileClicked(file) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pathTextView = findViewById(R.id.tv_current_path)
        recyclerView = findViewById(R.id.recycler_view)
        btnUp = findViewById(R.id.btn_up)
        btnHome = findViewById(R.id.btn_home)

        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = adapter

        btnUp.setOnClickListener { navigateUp() }

        btnHome.setOnClickListener {
            if (checkPermissions()) {
                val home = getSafeHomeDirectory()
                loadDirectory(home)
            } else {
                requestPermissions()
            }
        }

        // === STARTUP PERMISSION CHECK ===
        if (checkPermissions()) {
            loadDirectory(getSafeHomeDirectory())
        } else {
            requestPermissions()
        }
    }

    // === NEW PERMISSION LOGIC ===
    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            Environment.isExternalStorageManager()
        } else {
            // Android 5 - 10
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Request "All Files Access"
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                startActivityForResult(intent, 200)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivityForResult(intent, 200)
            }
        } else {
            // Android 5 - 10: Standard Request
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                100
            )
        }
    }

    // Handle the result from the "All Files Access" screen
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    loadDirectory(getSafeHomeDirectory())
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Handle the result from the Standard request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDirectory(getSafeHomeDirectory())
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ... Rest of the helper functions remain the same ...
    private fun getSafeHomeDirectory(): File {
        val standard = Environment.getExternalStorageDirectory()
        if (standard.canRead()) return standard
        val sdcard = File("/sdcard")
        if (sdcard.exists() && sdcard.canRead()) return sdcard
        return File("/")
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile
        if (parent != null) {
            loadDirectory(parent)
        } else {
            Toast.makeText(this, "Cannot go up further", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDirectory(dir: File) {
        if (!dir.exists() || !dir.canRead()) {
            Toast.makeText(this, "Access Denied: ${dir.name}", Toast.LENGTH_SHORT).show()
            return
        }

        currentDir = dir
        pathTextView.text = dir.absolutePath

        val files = dir.listFiles()
        if (files == null) {
            Toast.makeText(this, "System blocked access", Toast.LENGTH_SHORT).show()
            adapter.submitList(emptyList())
            return
        }

        val sortedFiles = files.toList().sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        adapter.submitList(sortedFiles)
        recyclerView.scrollToPosition(0)
    }

    private fun onFileClicked(file: File) {
        if (file.isDirectory) {
            loadDirectory(file)
        } else {
            returnResult(file)
        }
    }

    private fun returnResult(file: File) {
        val uri = FileProvider.getUriForFile(this, "com.cinemint.tvpicker.provider", file)
        val resultIntent = Intent()
        resultIntent.data = uri
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        val parent = currentDir.parentFile
        val home = getSafeHomeDirectory()
        if (parent != null && currentDir.absolutePath != home.absolutePath) {
            loadDirectory(parent)
        } else {
            super.onBackPressed()
        }
    }
}

// (Adapter class remains the same)
class FileAdapter(private val onClick: (File) -> Unit) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    private var files: List<File> = emptyList()

    fun submitList(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.textView.text = file.name
        holder.imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        if (file.isDirectory) {
            holder.imageView.setImageResource(R.drawable.ic_folder)
        } else {
            if (isImage(file)) {
                holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(holder.itemView.context)
                    .load(file)
                    .placeholder(R.drawable.ic_file)
                    .into(holder.imageView)
            } else {
                holder.imageView.setImageResource(R.drawable.ic_file)
            }
        }
        holder.itemView.setOnClickListener { onClick(file) }
    }

    private fun isImage(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".bmp") ||
                name.endsWith(".webp")
    }

    override fun getItemCount() = files.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.tv_name)
        val imageView: ImageView = view.findViewById(R.id.iv_icon)
    }
}