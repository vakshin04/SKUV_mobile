package com.vksh.skuv

import Catalano.Imaging.FastBitmap
import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import com.vksh.skuv.ml.EyeChecker
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    //Обозначение первоначальных настроек приложения
    private lateinit var cameraExecutor: ExecutorService
    private var selector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private lateinit var model: EyeChecker
    lateinit var camera: androidx.camera.core.Camera
    lateinit var speedDialView: SpeedDialView
    var score: Int = 0
    var flash: Boolean = true
    var vibrate: Boolean = true
    var showAnalyzer: Boolean = true
    var clc: Int = 0
    //Начало работы функциональной части приложения
    override fun onCreate(savedInstanceState: Bundle?) {
        //вызов служебных активностей
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //подгружаем нашу модель глубокого обучения
        model = EyeChecker.newInstance(this)
        //закрепляем ориентацию экрана
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        //настройка меню
        speedDialView = speedDial
        //кнопка вибрации
        speedDialView.addActionItem(
            SpeedDialActionItem.Builder(R.id.vibration, R.drawable.ic_vibration)
                .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))//цвет иконки
                .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))//цвет заднего фона
                .setLabel("Active Vibration")//название
                .setLabelColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))//цвет названия
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))//цвет заднего фона названия
                .create())
        //кнопка светового оповещения
        speedDialView.addActionItem(
            SpeedDialActionItem.Builder(R.id.flash, R.drawable.ic_flash)
                .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                .setLabel("Active Flash")
                .setLabelColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                .create())
        //кнопка отображения анализатора
        speedDialView.addActionItem(
            SpeedDialActionItem.Builder(R.id.analyzer, R.drawable.ic_analyzer)
                .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                .setLabel("Active Analyzer")
                .setLabelColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                .create())
        //обработчик событий меню
        speedDialView.setOnActionSelectedListener(SpeedDialView.OnActionSelectedListener { actionItem ->
            when (actionItem.id) {
                R.id.flash -> {//обработка кнопки светового отображения
                    if(!flash){//активный
                        speedDialView.replaceActionItem(speedDialView.actionItems[1],SpeedDialActionItem.Builder(R.id.flash, R.drawable.ic_flash)
                                .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                                .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                                .setLabel("Active Flash")
                                .setLabelColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                                .create())
                    }else{//неактивный
                        speedDialView.replaceActionItem(speedDialView.actionItems[1], SpeedDialActionItem.Builder(R.id.flash, R.drawable.ic_flash)
                                .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.white, theme))
                                .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                                .setLabel("Inactive Flash")
                                .setLabelColor(ResourcesCompat.getColor(resources, R.color.white, theme))
                                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                                .create())
                    }
                    flash =!flash
                    return@OnActionSelectedListener true
                }
                R.id.vibration -> {//обработка кнопки вибрации
                    if(!vibrate){//активный
                        speedDialView.replaceActionItem(speedDialView.actionItems[0],SpeedDialActionItem.Builder(R.id.vibration, R.drawable.ic_vibration)
                            .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                            .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                            .setLabel("Active Vibration")
                            .setLabelColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                            .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                            .create())
                    }else{//неактивный
                        speedDialView.replaceActionItem(speedDialView.actionItems[0],SpeedDialActionItem.Builder(R.id.vibration, R.drawable.ic_vibration)
                            .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.white, theme))
                            .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                            .setLabel("Inactive Vibration")
                            .setLabelColor(ResourcesCompat.getColor(resources, R.color.white, theme))
                            .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                            .create())
                    }
                    vibrate =!vibrate
                    return@OnActionSelectedListener true
                }
                R.id.analyzer -> {//обработка кнопки отображения аналайзера
                    if(!showAnalyzer){//активный
                        speedDialView.replaceActionItem(speedDialView.actionItems[2], SpeedDialActionItem.Builder(R.id.analyzer, R.drawable.ic_analyzer)
                            .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                            .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                            .setLabel("Active analyzer")
                            .setLabelColor(ResourcesCompat.getColor(resources, R.color.colorOnActive, theme))
                            .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                            .create())
                    }else{//неактивный
                        speedDialView.replaceActionItem(speedDialView.actionItems[2], SpeedDialActionItem.Builder(R.id.analyzer, R.drawable.ic_analyzer)
                            .setFabImageTintColor(ResourcesCompat.getColor(resources, R.color.white, theme))
                            .setFabBackgroundColor(resources.getColor(R.color.colorSecondaryVariant, theme))
                            .setLabel("Inactive analyzer")
                            .setLabelColor(ResourcesCompat.getColor(resources, R.color.white, theme))
                            .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.colorSecondaryVariant, theme))
                            .create())
                    }
                    showAnalyzer =!showAnalyzer//изменяем отображение анализатора
                    if(!showAnalyzer){
                        this.analyzer_screen.visibility = View.INVISIBLE
                    }else{
                        this.analyzer_screen.visibility = View.VISIBLE
                    }
                    return@OnActionSelectedListener true
                }
            }
            false
        })
        //проверка на загрузку OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV not loaded")
        } else {
            Log.d(TAG, "OpenCV loaded")
        }
        //проверяем разрешения на камеру, вибрацию и прочие оповещения
        if (allPermissionsGranted()) {
            startCamera()
        } else { //если нет - запрашиваем разрешение
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        //приложение начинает активную работу
        //проверка установленной камеры и изменение её ориентации
        camera_switch_button.setOnClickListener {
            if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                selector = CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                selector = CameraSelector.DEFAULT_FRONT_CAMERA
            }
            this.scr.text=getString(R.string.usual) //обнуляем показатели
            startCamera()
        }
    }
    //начинаем использовать библиотеку CameraX, подключаемся к камере
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            //отображение изображения на главном экране
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }
            //покадровая передача изображений в нашу функцию обработки
            val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(
                                cameraExecutor, FaceAnalyzer(//ставим класс
                                this,
                                selector == CameraSelector.DEFAULT_FRONT_CAMERA
                        )
                        )
                    }
            //запускаем камеру
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                        this, selector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
        this.scr.text = getString(R.string.usual)//обнулили показатели
    }
    //закрываем поток с камеры
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    //кнопка назад - закрытие меню - закрытие приложения
    override fun onBackPressed() {
        // Closes menu if its opened.
        if (speedDialView.isOpen) {
            speedDialView.close()
        } else {
            super.onBackPressed()
        }
    }
    //проверка на наличие всех разрешений
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
    //функция на запрос разрешений
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(//информирование об отказе в разрешении
                        this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
    //константы
    companion object {
        private const val TAG = "LogTag"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.VIBRATE)
    }
    //наш класс
    private class FaceAnalyzer(
            private val m: MainActivity,//ссылка на объект активности для обращения к полям приложения
            private val rotate: Boolean//разворот изображения при тыльной камере
    ) : ImageAnalysis.Analyzer {
        private var eye: Bitmap? = null
        //начало АНАЛИЗА
        override fun analyze(image: ImageProxy) {//получение входного изображения с камеры
            var k: Bitmap = image.toBitmap()//переводим в битовое изображение
            if(k.config != Bitmap.Config.ARGB_8888){//восстанавливаем цветовую гамму
                k = k.toRGB888()
            }
            if(rotate) {
                k = RotateBitmap(k, 270.0F)
            }else{
                k = RotateBitmap(k, 90.0F)
            }//поворот изображения в зависимости от камеры
            //поиск лица на изображении с камеры
            detect(k, R.raw.haarcascade_frontalface_alt, "haarcascade_frontalface_alt.xml", false)
            //поиск левого глаза на изображении с камеры
            detect(k, R.raw.haarcascade_lefteye_2splits, "haarcascade_lefteye_2splits.xml", true)
            var l_i = false
            var l_found =  false
            if(eye !=null){
                l_found = true
                val fb = FastBitmap(eye)
                fb.toGrayscale()//оттенки серого, библиотека Catalano
                //преобразование изображений в необходимый формат битовых данных, работа с TensorFlow Lite
                eye = fb.toBitmap()
                var tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(eye);
                val imageProcessor = ImageProcessor.Builder()
                        .add(ResizeOp(24, 24, ResizeOp.ResizeMethod.BILINEAR))
                        .build()
                tensorImage = imageProcessor.process(tensorImage)
                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(24, 24, 1), DataType.FLOAT32)
                val input = getByteBuffer(tensorImage.bitmap)
                inputFeature0.loadBuffer(input!!)
                val outputs = m.model.process(inputFeature0)//вызов обработки нашей моделью
                outputs.outputFeature0AsTensorBuffer.shape
                //результат работы модели
                Log.i("Result_model", "Левый глаз открыт: " + (outputs.outputFeature0AsTensorBuffer.intArray[1] == 1).toString())
                l_i = (outputs.outputFeature0AsTensorBuffer.intArray[1] == 1)//присваиваем значение в зависимости от состояния глаза
                eye = null
            }
            //поиск правого глаза на изображении с камеры
            detect(k, R.raw.haarcascade_righteye_2splits, "haarcascade_righteye_2splits.xml", true)
            var r_i = false
            var r_found =  false
            if(eye !=null){
                r_found = true
                val fb = FastBitmap(eye)
                fb.toGrayscale()//оттенки серого, библиотека Catalano
                //преобразование изображений в необходимый формат битовых данных, работа с TensorFlow Lite
                eye = fb.toBitmap()
                var tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(eye);
                val imageProcessor = ImageProcessor.Builder()
                        .add(ResizeOp(24, 24, ResizeOp.ResizeMethod.BILINEAR))
                        .build()
                tensorImage = imageProcessor.process(tensorImage)
                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(24, 24, 1), DataType.FLOAT32)
                val input = getByteBuffer(tensorImage.bitmap)
                inputFeature0.loadBuffer(input!!)
                val outputs = m.model.process(inputFeature0)//вызов обработки нашей моделью
                outputs.outputFeature0AsTensorBuffer.shape
                //результат работы модели
                Log.i("Result_model", "Правый глаз открыт: " + (outputs.outputFeature0AsTensorBuffer.intArray[1] == 1).toString())
                r_i = (outputs.outputFeature0AsTensorBuffer.intArray[1] == 1)//присваиваем значение в зависимости от состояния глаза
                eye = null
            }
            //управление счетчиком
            if(l_found and r_found){
                if(!l_i and !r_i){//увеличиваем, закрыты
                    m.score=m.score+2
                }else{//уменьшаем, открыты
                    m.score=m.score-2
                }
                //удерживаем граничные показатели в границах от 0 до 15
                if( m.score<0){
                    m.score=0
                }
                if( m.score>15){
                    m.score=15
                }
                //вывод шкалыы показателей
                m.runOnUiThread(Runnable {//потоки с user interface
                    m.scr.text = ("Danger: " + m.score.toString())
                })
            }
            //измерение кадров в секунду
            m.clc =m.clc+1
            Log.i("Counter", m.clc.toString())
            //опасная ситуация
            if( m.score==15){
                m.back.setCardBackgroundColor(m.resources.getColor(R.color.colorDanger))
                val vibrator = m.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator//вибрация
                val canVibrate: Boolean = vibrator.hasVibrator()//разрешение на вибрацию
                val milliseconds = 1000L
                if(m.vibrate){
                if (canVibrate) {//вибрация
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                        milliseconds,
                                        VibrationEffect.DEFAULT_AMPLITUDE
                                )
                        )
                    } else {
                        vibrator.vibrate(milliseconds)
                    }
                } }
                val mMediaPlayer= MediaPlayer.create(m, R.raw.alert);//звуковой эффект
                mMediaPlayer.start()
                if(m.flash) {//световое оповещение
                    if (m.selector == CameraSelector.DEFAULT_BACK_CAMERA) {//фонарик
                        m.camera.cameraControl.enableTorch(true)
                    }else{//фронтальная камера - белая панель
                        m.runOnUiThread(Runnable {
                            m.flash_screen.visibility=View.VISIBLE
                        })
                    }
                }
            }else{//возвращение в штатное состояние
                m.back.setCardBackgroundColor(m.resources.getColor(R.color.colorGoodState))
                m.camera.cameraControl.enableTorch(false)
                m.flash_screen.visibility=View.INVISIBLE
            }
            image.close()
        }
        //преобразование изображения во входной формат TF
        private fun getByteBuffer(bitmap: Bitmap): ByteBuffer? {
            val width = bitmap.width
            val height = bitmap.height
            val mImgData = ByteBuffer
                    .allocateDirect(4 * width * height)
            mImgData.order(ByteOrder.nativeOrder())
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            for (pixel in pixels) {
                mImgData.putFloat(Color.red(pixel).toFloat())
            }
            return mImgData
        }
        //использование классификаторов opencv
        fun detect(btm: Bitmap, xml_id: Int, xml_name: String, ch_eye: Boolean): Canvas{
            val mCascadeFile: File
            val k: Bitmap = btm
            //разворачиваем и подгружаем классификаторы
            var cascadeClassifier: CascadeClassifier = CascadeClassifier()
            try {
                val ins = m.resources.openRawResource(xml_id)
                mCascadeFile = File(
                        m.getDir("cascade", MODE_PRIVATE),
                        xml_name
                )
                val os = FileOutputStream(mCascadeFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (ins.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
                ins.close()
                os.close()
                cascadeClassifier = CascadeClassifier(mCascadeFile.absolutePath)
            } catch (e: IOException) {//обработка исключений
                Log.i(TAG, "face cascade not found")
            }
            //преобразовываем для opencv
            val sourceImage = Mat(k.height, k.width, CvType.CV_8UC4)
            Utils.bitmapToMat(k, sourceImage)
            Imgproc.cvtColor(sourceImage, sourceImage, Imgproc.COLOR_RGB2GRAY)
            val faces: MatOfRect = MatOfRect()
            //обнаружение области лица и глаз
            cascadeClassifier.detectMultiScale(sourceImage, faces)
            val canvas = Canvas(k)//преобразование изображения в холст для рисования границ объектов
            val facesArray: Array<out org.opencv.core.Rect> = faces.toArray()//извлекаем найденные объекты (массив)
            Imgproc.cvtColor(sourceImage, sourceImage, Imgproc.COLOR_GRAY2RGB, 4)//возвращаем натуральный цвет
            if(facesArray.size>0) {//обработка найденного - рисование
                if(ch_eye) {//если глаз, то запоминаем
                    eye = Bitmap.createBitmap(
                            k,
                            facesArray[0].tl().x.toInt(),
                            facesArray[0].tl().y.toInt(),
                            facesArray[0].width,
                            facesArray[0].height
                    )
                }
                val rectangle = Rect(//создаем объект
                        facesArray[0].tl().x.toInt(),
                        facesArray[0].tl().y.toInt(),
                        facesArray[0].br().x.toInt(),
                        facesArray[0].br().y.toInt()
                )
                //настройка кисточки
                val p = Paint()
                p.color = Color.GREEN
                p.style = Paint.Style.STROKE
                canvas.drawRect(rectangle, p)//рисуем границы
            }
            //возвращаем нарисованные границы в окно анализатора
            m.runOnUiThread(Runnable {
                m.imv.setImageBitmap(k)
                m.imv.draw(canvas)
            })
            return canvas
        }
        //разворачиваем изображение
        fun RotateBitmap(source: Bitmap, angle: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(angle)
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        //преобразовываем из CameraX в Bitmap
        fun ImageProxy.toBitmap(): Bitmap {
            val yBuffer = planes[0].buffer // Y
            val vuBuffer = planes[2].buffer // VU

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        //преобразовываем в rgb-формат
        private fun Bitmap.toRGB888(): Bitmap {
            val numPixels = this.width * this.height
            val pixels = IntArray(numPixels)
            this.getPixels(pixels, 0, this.width, 0, 0, this.width, this.height)
            val result = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
            result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
            return result
        }


    }

}
