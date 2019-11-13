package com.works.resumeuploader

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.android.billingclient.api.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {
    override fun onPurchasesUpdated(
        billingResult: BillingResult?,
        purchases: MutableList<Purchase>?
    ) {

    }

    private val READ_REQUEST_CODE: Int = 42

    lateinit private var billingClient: BillingClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStart() {
        super.onStart()

        billingClient =
            BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                    val skuDetailsParams =
                        SkuDetailsParams.newBuilder().setSkusList(listOf("inapp_online_upload"))
                            .setType(BillingClient.SkuType.INAPP)

                    billingClient.querySkuDetailsAsync(skuDetailsParams.build()) { billingResult2, skuDetailsList ->
                        // Process the result.
                        buy_paid.setOnClickListener {


                            // Retrieve a value for "skuDetails" by calling querySkuDetailsAsync().
                            val flowParams = BillingFlowParams.newBuilder()
                                .setSkuDetails(skuDetailsList.first())
                                .build()
                            val responseCode =
                                billingClient.launchBillingFlow(this@MainActivity, flowParams)
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {

            }
        })



        showFiles()





        startUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                .apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
            startActivityForResult(intent, READ_REQUEST_CODE)
        }

        imageView.setOnClickListener {
            if (textview.visibility == View.VISIBLE) {
                startUpload.performClick()
            } else {
                val uri = FileProvider.getUriForFile(
                    this,
                    applicationContext
                        .packageName + ".fileprovider", filesDir.listFiles()!!.first()
                )


                val uid = Binder.getCallingUid()
                val callingPackage = packageManager.getNameForUid(uid)
                applicationContext.grantUriPermission(
                    callingPackage, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );


                Intent("com.example.RESULT_ACTION", uri).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    data = uri
                }.also { result ->
                    setResult(Activity.RESULT_OK, result)
                }
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun showFiles() {
        var image: Bitmap? = null

        if (filesDir.listFiles()!!.count() == 0) {
            imageView.setBackgroundColor(Color.LTGRAY)
            textview.visibility = View.VISIBLE
        }
        filesDir.listFiles()!!.forEach { file ->
            val pdfRenderer = PdfRenderer(
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            )
            pdfRenderer.use { pdfRenderer ->
                val page = pdfRenderer.openPage(0)
                val bitmapWidth = resources.displayMetrics.densityDpi / 72 * page.width
                val bitmapHeight = resources.displayMetrics.densityDpi / 72 * page.height
                val bitmap =
                    Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                image = bitmap
                page.close()


            }
        }
        if (image != null) {
            imageView.setImageBitmap(image)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            imageView.setBackgroundColor(Color.TRANSPARENT)
            textview.visibility = View.INVISIBLE
            val selectedPdfUri = data!!.data!!
            val cursor = contentResolver.query(selectedPdfUri, null, null, null, null)
            cursor.use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val iStream = contentResolver.openInputStream(selectedPdfUri)?.buffered()
                        ?.use { it.readBytes() }
                    val fileToWrite = File(filesDir, "Resume.pdf")
                    val fileOutputStream = FileOutputStream(fileToWrite)
                    fileOutputStream.write(iStream!!)
                    showFiles()
                }
            }
        }
    }
}
