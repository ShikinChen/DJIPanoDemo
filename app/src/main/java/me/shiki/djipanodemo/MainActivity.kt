package me.shiki.djipanodemo

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.permissionx.guolindev.PermissionX
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.common.flightcontroller.ConnectionFailSafeBehavior
import dji.common.mission.waypoint.*
import dji.common.model.LocationCoordinate2D
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.media.DownloadListener
import dji.sdk.media.MediaFile
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.launch
import me.shiki.djipanodemo.databinding.ActivityMainBinding
import me.shiki.djipanodemo.databinding.DialogDownloadProgressBinding
import org.opencv.android.InstallCallbackInterface
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque

class MainActivity : AppCompatActivity(), DJISDKManager.SDKManagerCallback, WaypointMissionOperatorListener {

    companion object {
        private const val TAG = "DJI_PANO_DEMO"
        private const val CAPTURE_IMAGE_NUMBER = 8
    }


    private lateinit var binding: ActivityMainBinding
    private lateinit var dialogDownloadProgressBinding: DialogDownloadProgressBinding
    private var downloadProgressDialog: MaterialDialog? = null
    private val stitchingSourceImagesDirectory by lazy {
        getExternalFilesDir(null)!!
    }
    private val stitchingResultImagesDirectory by lazy {
        getExternalFilesDir("result")!!
    }

    private val djiSDKManager by lazy {
        DJISDKManager.getInstance()
    }

    private val aircraft by lazy {
        djiSDKManager.product as Aircraft
    }

    private val flightController by lazy {
        aircraft.flightController
    }

    private val camera by lazy {
        aircraft.camera
    }

    private val gimbal by lazy {
        aircraft.gimbal
    }

    private val missionControl by lazy {
        DJISDKManager.getInstance().missionControl
    }

    private val waypointMissionOperator by lazy {
        missionControl.waypointMissionOperator
    }

    private var aircraftLat = 0.0
    private var aircraftLng = 0.0

    private val mediaFileList by lazy {
        mutableListOf<MediaFile>()
    }

    private val imgList by lazy {
        LinkedBlockingDeque<String>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.fpvWidget.setSourceCameraNameVisibility(false)
        setContentView(binding.root)
        initUIControls()
        initDJISDK()

        Log.d(TAG, "OpenCVVersion:${OpenCVUtils.openCVVersion()}")
    }

    private fun initDJICamera() {
        camera.also {
            it.setMediaFileCallback { mediaFile ->
                when (mediaFile.mediaType) {
                    MediaFile.MediaType.JPEG -> {
                        Log.d(TAG, "${mediaFile.fileName}")
                        mediaFileList.add(mediaFile)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun initDJISDK() {
        PermissionX.init(this)
            .permissions(
                Manifest.permission.VIBRATE, // Gimbal rotation
                Manifest.permission.INTERNET, // API requests
                Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
                Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
                Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
                Manifest.permission.ACCESS_FINE_LOCATION, // Maps
                Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
                Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
                Manifest.permission.BLUETOOTH, // Bluetooth connected products
                Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
                Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
                Manifest.permission.READ_PHONE_STATE, // Device UUID accessed upon registration
                Manifest.permission.RECORD_AUDIO // Speaker accessory
            ).request { allGranted, _, deniedList ->
                if (allGranted) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        DJISDKManager.getInstance().registerApp(application, this@MainActivity)
                    }
                }
            }
    }

    private fun initUIControls() {
        binding.testButton.setOnClickListener {
            cleanSourceFolder()
            binding.testButton.isEnabled = false
            binding.testButton.setText(R.string.one_key_panorama)
            initAircraftMission()
        }
        binding.commonMessageTextView.text = null
        binding.testButton.isEnabled = false
        binding.testButton.setText(R.string.one_key_panorama)
        initDownloadProgressDialog()
    }

    private fun initFlightController() {
        flightController.setStateCallback { flightControllerState ->
            val controllerLat = flightControllerState.homeLocation.latitude
            val controllerLng = flightControllerState.homeLocation.longitude
            if (aircraftLat == 0.0 || aircraftLng == 0.0) {
                aircraftLat = controllerLat
                aircraftLng = controllerLng
                flightController
                    .setHomeLocation(LocationCoordinate2D(aircraftLat, aircraftLng)) {
                        if (it != null) {
                            Log.e(TAG, it.description)
                        }
                    }
                Log.d(TAG, "aircraft_location:${aircraftLat},${aircraftLng}")
            }
        }

        flightController
            .setGoHomeHeightInMeters(60) {
                if (it != null) {
                    Log.e(TAG, it.description)
                }
            }

        flightController.setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.GO_HOME) {
            if (it != null) {
                Log.e(TAG, it.description)
            }
        }

        flightController.setSeriousLowBatteryWarningThreshold(15) {
            if (it != null) {
                Log.e(TAG, it.description)
            }
        }

        flightController.setSmartReturnToHomeEnabled(true) {
            if (it != null) {
                Log.e(TAG, it.description)
            }
        }
    }

    private fun initAircraftMission() {
        lifecycleScope.launch(Dispatchers.IO) {
            mediaFileList.clear()
            showCommonMessage(getString(R.string.groundstation_take_control))
            val altitude = 50f
            val offset = 0.000005
            val waypoint1 = createWaypoint(aircraftLat + offset, aircraftLng, altitude)
            waypoint1.addAction(WaypointAction(WaypointActionType.ROTATE_AIRCRAFT, 0))

            val builder = WaypointMission.Builder()
//            builder.addWaypoint(waypoint1)
//            val waypoint2 = createWaypoint(aircraftLat, aircraftLng, altitude)
            addRotateAircraftAction(builder, -180, 180)

//            builder.addWaypoint(waypoint2)

            builder.autoFlightSpeed(10f).maxFlightSpeed(10f)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                .finishedAction(WaypointMissionFinishedAction.GO_HOME)
                .gotoFirstWaypointMode(
                    WaypointMissionGotoWaypointMode.SAFELY
                )
                .repeatTimes(1)
                .headingMode(WaypointMissionHeadingMode.AUTO)

            var err = builder.checkParameters()
            if (err != null) {
                showCommonMessage("Upload GroundStation Task failed:${err.description}")
                Log.e(TAG, err.description)
                return@launch
            }

            val mission = builder.build()

            showCommonMessage("Uploading GroundStation Task...")
            err = waypointMissionOperator.loadMission(mission)
            if (err == null) {
                showCommonMessage("Upload GroundStation Task success")
                uploadMission().filter {
                    if (it != null) {
                        showCommonMessage("Upload GroundStation Task failed:${it.description}")
                        Log.e(TAG, it.description)
                    }
                    it == null
                }.flatMapConcat {
                    startMission()
                }.filter {
                    if (it != null) {
                        showCommonMessage("Upload GroundStation Task failed:${it.description}")
                        Log.e(TAG, it.description)
                    }
                    it == null
                }.collect {
                    showCommonMessage("Start GroundStation Task success")
                }
            } else {
                showCommonMessage("Upload GroundStation Task failed:${err.description}")
                Log.e(TAG, err.description)
            }

        }
    }

    private fun startMission(): Flow<DJIError?> {
        return callbackFlow {
            waypointMissionOperator.startMission {
                trySendBlocking(it)
            }
            awaitClose { }
        }
    }

    private fun uploadMission(): Flow<DJIError?> {
        return callbackFlow {
            waypointMissionOperator.uploadMission {
                trySendBlocking(it)
            }
            awaitClose { }
        }
    }

    private fun createWaypoint(
        lat: Double,
        lng: Double,
        alt: Float = 50f,
        actionGimbal: WaypointAction = WaypointAction(WaypointActionType.GIMBAL_PITCH, 0),
    ): Waypoint {
        val waypoint = Waypoint(lat, lng, alt)
        waypoint.heading = 0
        waypoint.actionRepeatTimes = 1
        waypoint.actionTimeoutInSeconds = 60 * 10
        waypoint.turnMode = WaypointTurnMode.COUNTER_CLOCKWISE
        waypoint.addAction(actionGimbal)
        return waypoint
    }

    private fun addRotateAircraftAction(builder: WaypointMission.Builder, start: Int, end: Int, alt: Float = 50f) {
        val offset = 0.0000045
        for (i in start until end step 45) {
            var waypoint = createWaypoint(aircraftLat, aircraftLng, alt)
            waypoint.addAction(WaypointAction(WaypointActionType.ROTATE_AIRCRAFT, i))
            for (j in -45..0 step 30) {
                waypoint.addAction(WaypointAction(WaypointActionType.GIMBAL_PITCH, j))
                waypoint.addAction(WaypointAction(WaypointActionType.START_TAKE_PHOTO, 1))
            }
            waypoint.addAction(WaypointAction(WaypointActionType.GIMBAL_PITCH, 0))
            waypoint.addAction(WaypointAction(WaypointActionType.START_TAKE_PHOTO, 1))

            if (i == 0 || i == -180) {
                waypoint.addAction(WaypointAction(WaypointActionType.GIMBAL_PITCH, -75))
                waypoint.addAction(WaypointAction(WaypointActionType.START_TAKE_PHOTO, 1))
            }
            builder.addWaypoint(waypoint)
            waypoint = createWaypoint(aircraftLat + offset, aircraftLng, alt)
            builder.addWaypoint(waypoint)
        }
//        waypoint.addAction(WaypointAction(WaypointActionType.GIMBAL_PITCH, 0))
    }

    private fun showCommonMessage(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (binding.commonMessageTextView.text != msg) {
                binding.commonMessageTextView.text = msg
            }
            binding.testButton.isEnabled = true
        }
    }

    private fun cleanSourceFolder() {
        stitchingSourceImagesDirectory?.let {
            it.listFiles()
        }?.forEach {
            if (!it.exists()) {
                it.delete()
            }
        }
    }

    private fun initDownloadProgressDialog() {
        dialogDownloadProgressBinding = DialogDownloadProgressBinding.inflate(layoutInflater)
    }

    private fun showDownloadProgressDialog() {
        downloadProgressDialog = MaterialDialog(this).show {
            customView(view = dialogDownloadProgressBinding.root)
            cancelable(false)
        }.show {

        }
    }

    override fun onRegister(error: DJIError?) {
        Log.d(TAG, "onRegister")
        if (error != null && error != DJISDKError.REGISTRATION_SUCCESS) {
            Log.e(TAG, "errorCode:${error.errorCode}\ndescription:${error.description}")
        } else {
            DJISDKManager.getInstance().startConnectionToProduct()
        }
    }

    override fun onProductDisconnect() {
        Log.d(TAG, "onProductDisconnect")
        lifecycleScope.launch(Dispatchers.Main) {
            binding.testButton.isEnabled = false
        }
    }

    override fun onProductConnect(product: BaseProduct?) {
        Log.d(TAG, "onProductConnect")
        if (product != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                binding.testButton.isEnabled = true
            }
            initDJICamera()
            initFlightController()
            waypointMissionOperator.addListener(this)
        }
    }

    override fun onProductChanged(product: BaseProduct?) {
        Log.d(TAG, "onProductChanged")
    }

    override fun onComponentChange(
        key: BaseProduct.ComponentKey?,
        oldComponent: BaseComponent?,
        newComponent: BaseComponent?
    ) {
        Log.d(TAG, "onComponentChange")
    }

    override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
        Log.d(TAG, "onInitProcess")
    }

    override fun onDatabaseDownloadProgress(current: Long, total: Long) {
        Log.d(TAG, "onDatabaseDownloadProgress")
    }

    override fun onDownloadUpdate(event: WaypointMissionDownloadEvent) {
    }

    override fun onUploadUpdate(event: WaypointMissionUploadEvent) {
    }

    override fun onExecutionUpdate(event: WaypointMissionExecutionEvent) {
    }

    override fun onExecutionStart() {
    }

    override fun onExecutionFinish(err: DJIError?) {
        downloadImg()
    }

    private fun downloadImg() {
        if (mediaFileList.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.Main) {
                dialogDownloadProgressBinding.text.text =
                    "0/${mediaFileList.size}"
                showDownloadProgressDialog()
            }
            val dir = File(stitchingSourceImagesDirectory, System.currentTimeMillis().toString())
            for (mediaFile in mediaFileList) {
                val fileName = mediaFile.fileName.substring(0, mediaFile.fileName.lastIndexOf("."))
                mediaFile.fetchFileData(
                    dir,
                    fileName,
                    object : DownloadListener<String> {
                        override fun onStart() {
                        }

                        override fun onRateUpdate(p0: Long, p1: Long, p2: Long) {
                        }

                        override fun onRealtimeDataUpdate(p0: ByteArray?, p1: Long, p2: Boolean) {
                        }

                        override fun onProgress(total: Long, current: Long) {
                        }

                        override fun onSuccess(path: String?) {
                            Log.d(TAG, "$path/${mediaFile.fileName}")
                            if (!path.isNullOrEmpty()) {
                                imgList.add(path)
                                val progress = imgList.size * 100 / mediaFileList.size.toFloat()
                                lifecycleScope.launch(Dispatchers.Main) {
                                    dialogDownloadProgressBinding.progressBar.progress = progress.toInt()
                                    dialogDownloadProgressBinding.text.text =
                                        "${imgList.size}/${mediaFileList.size}"
                                    delay(1000)
                                    if (progress >= 100f) {
                                        downloadProgressDialog?.dismiss()
                                    }
                                }
                            }
                        }

                        override fun onFailure(err: DJIError?) {
                        }

                    })
            }
        }
    }

    override fun onDestroy() {
        waypointMissionOperator.removeListener(this)
        super.onDestroy()
    }
}