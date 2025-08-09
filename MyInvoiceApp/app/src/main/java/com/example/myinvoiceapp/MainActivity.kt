package com.example.myinvoiceapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var errorView: android.view.View

    private val startUrl = "https://saulgooddev.github.io/my-invoice-app"

    private val requestWriteStorage = 1001
    private var pendingPrintAttributes: PrintAttributes? = null
    private var currentPrintJob: PrintJob? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        val retryButton: com.google.android.material.button.MaterialButton = findViewById(R.id.retryButton)
        val printButton: MaterialButton = findViewById(R.id.printButton)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                errorView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request == null || request.isForMainFrame) {
                    showError(error?.description?.toString() ?: "Page load error")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request == null || request.isForMainFrame) {
                    val code = errorResponse?.statusCode ?: -1
                    showError("HTTP error $code while loading page")
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                showError("SSL error while loading page")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
            }
        }

        retryButton.setOnClickListener {
            errorView.visibility = View.GONE
            webView.reload()
        }

        printButton.setOnClickListener {
            startNativePrint()
        }

        webView.loadUrl(startUrl)

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_pdf -> {
                createWebPrintJob(webView)
                true
            }
            R.id.action_print -> {
                startNativePrint()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Adds A4 600dpi print attributes and triggers a silent export to Downloads
    private fun createWebPrintJob(webView: WebView) {
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        pendingPrintAttributes = attrs
        startPdfExport()
    }

    // Native print preview using Android's PrintManager. Supports Wi-Fi/Bluetooth printers via system print services
    private fun startNativePrint() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "InvoicePrint_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val printAdapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        Toast.makeText(this, getString(R.string.printing_started), Toast.LENGTH_SHORT).show()
        currentPrintJob = printManager.print(jobName, printAdapter, attributes)

        // Monitor status on next frames/ticks
        webView.postDelayed(object : Runnable {
            override fun run() {
                val job = currentPrintJob
                if (job != null) {
                    when {
                        job.isCancelled -> Toast.makeText(applicationContext, getString(R.string.printing_cancelled), Toast.LENGTH_SHORT).show()
                        job.isFailed -> Toast.makeText(applicationContext, getString(R.string.printing_failed), Toast.LENGTH_SHORT).show()
                        job.isCompleted -> Toast.makeText(applicationContext, getString(R.string.printing_completed), Toast.LENGTH_SHORT).show()
                        else -> {
                            // still processing; check again
                            webView.postDelayed(this, 1000)
                        }
                    }
                }
            }
        }, 1000)
    }

    private fun startPdfExport() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                ActivityCompat.requestPermissions(
                    this as Activity,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestWriteStorage
                )
                return
            }
        }
        exportWebViewToPdf(pendingPrintAttributes)
        pendingPrintAttributes = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestWriteStorage) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportWebViewToPdf(pendingPrintAttributes)
                pendingPrintAttributes = null
            } else {
                Toast.makeText(this, getString(R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportWebViewToPdf(attrs: PrintAttributes?) {
        Toast.makeText(this, getString(R.string.saving_pdf), Toast.LENGTH_SHORT).show()
        val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter("webview_print")
        val attributes = attrs ?: PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Invoice_${timeStamp}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri: Uri? = contentResolver.insert(collection, contentValues)
            if (uri == null) {
                Toast.makeText(this, getString(R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val pfd = contentResolver.openFileDescriptor(uri, "w")
            if (pfd == null) {
                Toast.makeText(this, getString(R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
                return
            }
            adapter.onStart()
            adapter.onLayout(null, attributes, null, object : PrintDocumentAdapter.LayoutResultCallback() {}, null)
            adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), pfd, null, object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pages: Array<out android.print.PageRange>?) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                    Toast.makeText(applicationContext, getString(R.string.pdf_saved, fileName), Toast.LENGTH_LONG).show()
                    pfd.close()
                    adapter.onFinish()
                }

                override fun onWriteFailed(error: CharSequence?) {
                    Toast.makeText(applicationContext, getString(R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
                    pfd.close()
                    adapter.onFinish()
                }
            })
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outFile = File(downloadsDir, fileName)
            val fos = FileOutputStream(outFile)
            val pfd = android.os.ParcelFileDescriptor.dup(fos.fd)

            adapter.onStart()
            adapter.onLayout(null, attributes, null, object : PrintDocumentAdapter.LayoutResultCallback() {}, null)
            adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), pfd, null, object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pages: Array<out android.print.PageRange>?) {
                    Toast.makeText(applicationContext, getString(R.string.pdf_saved, outFile.absolutePath), Toast.LENGTH_LONG).show()
                    fos.close()
                    pfd.close()
                    adapter.onFinish()
                }

                override fun onWriteFailed(error: CharSequence?) {
                    Toast.makeText(applicationContext, getString(R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
                    fos.close()
                    pfd.close()
                    adapter.onFinish()
                }
            })
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        val messageView = findViewById<android.widget.TextView>(R.id.errorMessage)
        messageView.text = message
    }
}