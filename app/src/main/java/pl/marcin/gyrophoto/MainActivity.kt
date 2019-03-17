package pl.marcin.gyrophoto

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager.SENSOR_DELAY_FASTEST
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.github.pwittchen.reactivesensors.library.ReactiveSensorEvent
import com.github.pwittchen.reactivesensors.library.ReactiveSensorFilter
import com.github.pwittchen.reactivesensors.library.ReactiveSensors
import io.fotoapparat.Fotoapparat
import io.fotoapparat.result.adapter.rxjava2.toSingle
import io.reactivex.BackpressureStrategy
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var fotoapparat: Fotoapparat

    private val compositeDisposable = CompositeDisposable()
    private var lastGyroValues: ReactiveSensorEvent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeCameraWithPermissions()
        takePhotoBt.setOnClickListener { takePhoto() }
    }

    private fun initializeCameraWithPermissions() {
        val cameraPermission = Manifest.permission.CAMERA
        val writeFilePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (checkPermissionGranted(cameraPermission) && checkPermissionGranted(writeFilePermission)) {
            fotoapparat = Fotoapparat(
                context = this,
                view = camera_view
            )
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(cameraPermission, writeFilePermission), 1234
            )
            finish()
        }
    }

    private fun checkPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun initGyro() {
        compositeDisposable += ReactiveSensors(this).observeSensor(
            Sensor.TYPE_GYROSCOPE,
            SENSOR_DELAY_FASTEST,
            Handler(),
            BackpressureStrategy.LATEST
        )
            .subscribeOn(Schedulers.computation())
            .filter(ReactiveSensorFilter.filterSensorChanged())
            .subscribeBy(
                onNext = {
                    lastGyroValues = it
                    gyroText.text = lastGyroValues?.sensorEvent?.values?.joinToString("\n")
                },
                onError = { Log.e("TAG", "Error", it) }
            )
    }

    private fun takePhoto() {
        compositeDisposable +=
            getPhotoWithGyroSingle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { filePath ->
                        Toast.makeText(this, "Success: $filePath", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Log.e("TAG", "Error!", it) }
                )
    }

    private fun getSavePhotoSingle(file: File): Single<Unit> {
        return fotoapparat.takePicture()
            .saveToFile(file)
            .toSingle()
    }

    private fun getPhotoWithGyroSingle(): Single<String> {
        val file = createFile(lastGyroValues)
        return getSavePhotoSingle(file).ignoreElement().toSingleDefault(file.absolutePath)
    }

    private fun createFile(gyroEvent: ReactiveSensorEvent?): File {
        val fileName = "${Date()}_${gyroEvent?.sensorEvent?.values?.joinToString("_")}.jpg"
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        Log.e("TAG", file.absolutePath.toString())
        return file
    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
        initGyro()
    }

    override fun onStop() {
        compositeDisposable.clear()
        fotoapparat.stop()
        super.onStop()
    }
}
