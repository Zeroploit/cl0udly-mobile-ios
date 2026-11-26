@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIEvent
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * iOS QR reader: a full screen controller with the live camera preview, a dark frame around the
 * code and a close button. The first code read for that presentation wins and the camera stops.
 *
 * The controller is held in [activeScanner] until it closes, the way the pickers keep their
 * delegates: UIKit does not retain a presented controller past its dismissal animation.
 */
actual fun scanQrCode(onResult: (String?) -> Unit) {
    if (activeScanner != null) return onResult(null)
    val host = topViewController() ?: return onResult(null)
    if (AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) == null) return onResult(null)
    // iOS shows the camera prompt by itself the first time a session uses the camera, so the
    // scanner goes up and asks from its own view. A refusal leaves the preview black and the
    // close button working; the manual fields of the sheet stay underneath.
    val scanner = QrScannerController(onResult)
    if (!scanner.start()) return onResult(null)
    activeScanner = scanner
    host.presentViewController(scanner, animated = true, completion = null)
}

private var activeScanner: QrScannerController? = null

private class QrScannerController(
    private val onResult: (String?) -> Unit,
) : UIViewController(nibName = null, bundle = null), AVCaptureMetadataOutputObjectsDelegateProtocol {

    private val session = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer? = null
    private var overlay: QrOverlay? = null
    private var done = false

    /** Wires the camera in; false when the device or the input cannot be used. */
    fun start(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: return false
        session.beginConfiguration()
        if (!session.canAddInput(input)) {
            session.commitConfiguration()
            return false
        }
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) {
            session.commitConfiguration()
            return false
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
        // only QR codes, and only once the output joined the session: the list is checked there
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        session.commitConfiguration()
        return true
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.addSublayer(layer)
        preview = layer
        val frame = QrOverlay { finish(null) }
        view.addSubview(frame)
        overlay = frame
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        // `startRunning` blocks while the camera spins up: never on the main thread.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (!session.isRunning()) session.startRunning()
        }
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (session.isRunning()) session.stopRunning()
        }
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        // swiped away without a code: nobody else would answer the caller
        if (!done) {
            done = true
            if (activeScanner === this) activeScanner = null
            onResult(null)
        }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.setFrame(view.bounds)
        overlay?.setFrame(view.bounds)
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        if (done) return
        val text = didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstOrNull { !it.stringValue.isNullOrBlank() }
            ?.stringValue
        finish(text)
    }

    /** Closes the scanner once and hands the text (or null) back on the main thread. */
    private fun finish(text: String?) {
        if (done) return
        done = true
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (session.isRunning()) session.stopRunning()
        }
        dismissViewControllerAnimated(true) {
            if (activeScanner === this) activeScanner = null
            // the overlay's close button captures this controller: break the cycle after closing
            overlay?.removeFromSuperview()
            overlay = null
            onResult(text)
        }
    }
}

/** The dark frame: four shades around a square hole, a white outline, a hint and a way out. */
private class QrOverlay(private val onClose: () -> Unit) : UIView(frame = ZERO) {

    private val shades = List(4) { UIView(frame = ZERO) }
    private val outline = UIView(frame = ZERO)
    private val hint = UILabel(frame = ZERO)
    private val close = TapView(text = "Close", onTap = onClose)

    init {
        for (shade in shades) {
            shade.backgroundColor = UIColor.blackColor.colorWithAlphaComponent(0.55)
            shade.userInteractionEnabled = false
            addSubview(shade)
        }
        outline.userInteractionEnabled = false
        outline.layer.borderColor = UIColor.whiteColor.CGColor
        outline.layer.borderWidth = 2.0
        outline.layer.cornerRadius = 12.0
        addSubview(outline)
        hint.text = "Point the camera at the QR code"
        hint.textColor = UIColor.whiteColor
        hint.font = UIFont.systemFontOfSize(16.0)
        hint.textAlignment = NSTextAlignmentCenter
        hint.numberOfLines = 0L
        hint.userInteractionEnabled = false
        addSubview(hint)
        addSubview(close)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val w = CGRectGetWidth(bounds)
        val h = CGRectGetHeight(bounds)
        val side = minOf(w, h) * 0.7
        val x = (w - side) / 2.0
        val y = (h - side) / 2.0
        shades[0].setFrame(CGRectMake(0.0, 0.0, w, y))
        shades[1].setFrame(CGRectMake(0.0, y, x, side))
        shades[2].setFrame(CGRectMake(x + side, y, w - x - side, side))
        shades[3].setFrame(CGRectMake(0.0, y + side, w, h - y - side))
        outline.setFrame(CGRectMake(x, y, side, side))
        hint.setFrame(CGRectMake(24.0, y - 64.0, w - 48.0, 44.0))
        close.setFrame(CGRectMake((w - 120.0) / 2.0, h - 96.0, 120.0, 44.0))
    }
}

/** A view that fires once per tap, so "Close" needs no Objective-C selector. */
private class TapView(
    text: String,
    private val onTap: () -> Unit,
) : UIView(frame = ZERO) {

    private val label = UILabel(frame = ZERO)

    init {
        label.text = text
        label.textColor = UIColor.whiteColor
        label.font = UIFont.systemFontOfSize(17.0, weight = UIFontWeightSemibold)
        label.textAlignment = NSTextAlignmentCenter
        label.userInteractionEnabled = false
        addSubview(label)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        label.setFrame(bounds)
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesEnded(touches, withEvent)
        onTap()
    }
}

/** An empty rect: `CGRectZero` is already a `CValue`, so it is used as it comes. */
private val ZERO: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0)
