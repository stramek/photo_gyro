package pl.marcin.gyrophoto

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.github.pwittchen.reactivesensors.library.ReactiveSensorEvent
import com.github.pwittchen.reactivesensors.library.ReactiveSensorFilter
import com.github.pwittchen.reactivesensors.library.ReactiveSensors
import io.fotoapparat.Fotoapparat
import io.fotoapparat.result.BitmapPhoto
import io.fotoapparat.result.adapter.rxjava2.toSingle
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var fotoapparat: Fotoapparat

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeCameraWithPermissions()
        takePhotoBt.setOnClickListener { takePhoto() }
    }

    private fun initializeCameraWithPermissions() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED){
            fotoapparat = Fotoapparat(
                context = this,
                view = camera_view
            )
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1234)
            finish()
        }
    }

    private fun takePhoto() {
        compositeDisposable += Single.zip(
            getPhotoSingle(),
            getGyroSingle(),
            BiFunction { photo: BitmapPhoto, gyroValues: ReactiveSensorEvent -> photo to gyroValues }
        ).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { pair ->
                    val (photoBitmap, gyroValues) = pair
                    image.setImageBitmap(photoBitmap.bitmap)
                    image.rotation = -photoBitmap.rotationDegrees.toFloat()
                    gyrpParams.text = gyroValues.sensorEvent.values.joinToString()
                },
                onError = { Log.e("TAG", "Error!", it) }
            )
    }

    private fun getGyroSingle(): Single<ReactiveSensorEvent>? {
        return ReactiveSensors(this).observeSensor(Sensor.TYPE_GYROSCOPE)
            .subscribeOn(Schedulers.computation())
            .filter(ReactiveSensorFilter.filterSensorChanged())
            .firstOrError()
    }

    private fun getPhotoSingle(): Single<BitmapPhoto> {
        return fotoapparat.takePicture()
            .toBitmap()
            .toSingle()
    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
    }

    override fun onStop() {
        super.onStop()
        fotoapparat.stop()
    }
}
