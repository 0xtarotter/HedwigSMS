package com.hedwig.sms.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.hedwig.sms.R
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * 自定义扫码界面 / custom QR scanner screen.
 *
 * 用 zxing 的 CaptureManager 驱动相机与解码，配上优雅的暗化遮罩、绿色取景框、扫描线、
 * 手电筒与关闭按钮。Activity 在清单中锁定竖屏，彻底解决「随手机方向旋转」的问题。
 * Uses zxing's CaptureManager with an elegant overlay; locked to portrait in the manifest
 * so the scanner never rotates awkwardly with the device.
 */
class ScannerActivity : ComponentActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeView: DecoratedBarcodeView
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        barcodeView = findViewById(R.id.barcode_scanner)
        // 关闭自带取景框与状态文字，改用我们自己的遮罩 / use our own overlay instead.
        barcodeView.viewFinder.visibility = android.view.View.GONE
        barcodeView.setStatusText("")

        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()

        findViewById<TextView>(R.id.scanClose).setOnClickListener { finish() }

        val torch = findViewById<TextView>(R.id.scanTorch)
        torch.setOnClickListener {
            torchOn = !torchOn
            if (torchOn) barcodeView.setTorchOn() else barcodeView.setTorchOff()
            torch.text = getString(if (torchOn) R.string.scan_torch_off else R.string.scan_torch_on)
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
