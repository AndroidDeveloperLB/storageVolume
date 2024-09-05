package com.lb.storagevolume

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import com.example.myapplication.FileUtilEx
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.checkAccessButton).setOnClickListener {
            val persistedUriPermissions = contentResolver.persistedUriPermissions
            val storagePathsWeHaveAccessTo = HashSet<String>()
            Log.d("AppLog", "got access to paths:")
            for (persistedUriPermission in persistedUriPermissions) {
                val path = FileUtilEx.getFullPathFromTreeUri(this, persistedUriPermission.uri)
                        ?: continue
                Log.d("AppLog", "path: $path")
                storagePathsWeHaveAccessTo.add(path)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Log.d("AppLog", "storage volumes:")
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolumes = storageManager.storageVolumes
                for (storageVolume in storageVolumes) {
                    val volumePath = FileUtilEx.getVolumePath(storageVolume)
                    if (volumePath == null) {
                        Log.d("AppLog", "storageVolume \"${storageVolume.getDescription(this)}\" - failed to get volumePath")
                    } else {
                        val hasAccess = storagePathsWeHaveAccessTo.contains(volumePath)
                        Log.d("AppLog", "storageVolume \"${storageVolume.getDescription(this)}\" - volumePath:$volumePath - gotAccess? $hasAccess")
                    }
                }
            }
        }
        findViewById<View>(R.id.requestAccessButton).setOnClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val primaryVolume = storageManager.primaryStorageVolume
                primaryVolume.createOpenDocumentTreeIntent()
            } else {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            }
            startActivityForResult(intent, 1)
        }
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.main, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                val url: String = when (item.itemId) {
                    R.id.menuItem_all_my_apps -> "https://play.google.com/store/apps/developer?id=AndroidDeveloperLB"
                    R.id.menuItem_all_my_repositories -> "https://github.com/AndroidDeveloperLB"
                    R.id.menuItem_current_repository_website -> "https://github.com/AndroidDeveloperLB/storageVolume"
                    else -> return true
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                @Suppress("DEPRECATION")
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivity(intent)
                return true
            }
        }, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK)
            return
        val treeUri: Uri = data?.data ?: return
        thread {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            val fullPathFromTreeUri = FileUtilEx.getFullPathFromTreeUri(this, treeUri)
            Log.d("AppLog", "fullPathFromTreeUri :$fullPathFromTreeUri ")
            if (fullPathFromTreeUri != null) {
                val folder = File(fullPathFromTreeUri)
                val children = folder.listFiles()
                Log.d("AppLog", "isDirectory?${folder.isDirectory} listFiles:${children?.size} canRead?${folder.canRead()} ")
//                if (!children.isNullOrEmpty()) {
//                    Log.d("AppLog", "children:")
//                    for (child in children) {
//                        Log.d("AppLog", "child:${child.absolutePath} canRead?${child.canRead()} isFile?${child.isFile} length:${child.lengthEx()}")
//                    }
//                }
            }
        }
    }
}
