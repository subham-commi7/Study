package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.DocumentEntity
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    document: DocumentEntity,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Page Bitmaps Cache
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    // Zoom & Pan transformation states
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    // Renderer reference held across renders
    var pdfRendererRef by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfdRef by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Load PDF
    LaunchedEffect(document.filePath) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                var pfd: ParcelFileDescriptor? = null

                // Strategy 1: Check direct filePath as File
                val directFile = File(document.filePath)
                if (directFile.exists() && directFile.length() > 0L) {
                    pfd = ParcelFileDescriptor.open(directFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }

                // Strategy 2: If filePath is content:// URI
                if (pfd == null && document.filePath.startsWith("content://")) {
                    try {
                        pfd = context.contentResolver.openFileDescriptor(android.net.Uri.parse(document.filePath), "r")
                    } catch (_: Exception) {}
                }

                // Strategy 3: Check academic_docs fallback directory
                if (pfd == null) {
                    val docsDir = File(context.filesDir, "academic_docs")
                    val fallbackFile = File(docsDir, document.fileName)
                    if (fallbackFile.exists() && fallbackFile.length() > 0L) {
                        pfd = ParcelFileDescriptor.open(fallbackFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else if (docsDir.exists()) {
                        val matching = docsDir.listFiles { _, name ->
                            name.endsWith(document.fileName, ignoreCase = true) ||
                            (document.fileName.isNotBlank() && name.contains(document.fileName.substringBeforeLast("."), ignoreCase = true))
                        }?.firstOrNull { it.length() > 0L }
                        if (matching != null) {
                            pfd = ParcelFileDescriptor.open(matching, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    }
                }

                // Strategy 4: If remote HTTP/HTTPS or gs:// URL, download to cache
                if (pfd == null && (document.filePath.startsWith("http://") || document.filePath.startsWith("https://"))) {
                    try {
                        val docsDir = File(context.filesDir, "academic_docs").apply { mkdirs() }
                        val cachedFile = File(docsDir, "download_${System.currentTimeMillis()}_${document.fileName.ifBlank { "doc.pdf" }}")
                        val url = java.net.URL(document.filePath)
                        url.openStream().use { input ->
                            cachedFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (cachedFile.exists() && cachedFile.length() > 0L) {
                            pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    } catch (_: Exception) {}
                }

                if (pfd == null) {
                    errorMessage = "This PDF document is not available on local device storage. Please re-upload or import the file again."
                    isLoading = false
                    return@withContext
                }

                val renderer = PdfRenderer(pfd)
                pfdRef = pfd
                pdfRendererRef = renderer
                pageCount = renderer.pageCount

                // Render initial page 0
                renderPage(renderer, 0, pageBitmaps)
                isLoading = false

                // Pre-render subsequent pages in background
                val maxPreload = minOf(pageCount, 10)
                for (i in 1 until maxPreload) {
                    renderPage(renderer, i, pageBitmaps)
                }
            } catch (e: Exception) {
                errorMessage = "Unable to open PDF: ${e.localizedMessage ?: "Invalid or corrupt file."}"
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRendererRef?.close()
                pfdRef?.close()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = document.title.ifBlank { document.fileName },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = if (pageCount > 0) "Page ${currentPageIndex + 1} of $pageCount" else "Loading...",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // Zoom Out
                IconButton(
                    onClick = {
                        scale = (scale - 0.25f).coerceAtLeast(0.75f)
                    }
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                }

                // Reset Zoom
                IconButton(
                    onClick = {
                        scale = 1.0f
                        offsetX = 0f
                        offsetY = 0f
                    }
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White)
                }

                // Zoom In
                IconButton(
                    onClick = {
                        scale = (scale + 0.25f).coerceAtMost(3.0f)
                    }
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate800)
        )

        // Main Viewer Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.75f, 3.5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = AcademicBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Opening PDF document...", color = Color.White, fontSize = 13.sp)
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = onBack,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AcademicBlue)
                        ) {
                            Text("Go Back")
                        }
                    }
                }

                pageCount > 0 -> {
                    val currentBitmap = pageBitmaps[currentPageIndex]
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                        )
                    } else {
                        // Render on demand if not cached
                        LaunchedEffect(currentPageIndex) {
                            withContext(Dispatchers.IO) {
                                pdfRendererRef?.let { renderer ->
                                    renderPage(renderer, currentPageIndex, pageBitmaps)
                                }
                            }
                        }
                        CircularProgressIndicator(color = AcademicBlue)
                    }
                }
            }
        }

        // Bottom Page Navigation Bar
        if (pageCount > 1) {
            Surface(
                color = Slate800,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentPageIndex > 0) {
                                currentPageIndex--
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                scope.launch(Dispatchers.IO) {
                                    pdfRendererRef?.let { renderPage(it, currentPageIndex, pageBitmaps) }
                                }
                            }
                        },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Page",
                            tint = if (currentPageIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1} / $pageCount",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    IconButton(
                        onClick = {
                            if (currentPageIndex < pageCount - 1) {
                                currentPageIndex++
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                scope.launch(Dispatchers.IO) {
                                    pdfRendererRef?.let { renderPage(it, currentPageIndex, pageBitmaps) }
                                }
                            }
                        },
                        enabled = currentPageIndex < pageCount - 1
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Page",
                            tint = if (currentPageIndex < pageCount - 1) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

private fun renderPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: MutableMap<Int, Bitmap>
) {
    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return
    if (cache.containsKey(pageIndex)) return

    try {
        val page = renderer.openPage(pageIndex)
        // High quality scale: 2x base resolution for sharp crisp text
        val width = page.width * 2
        val height = page.height * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        cache[pageIndex] = bitmap
    } catch (_: Exception) {}
}
