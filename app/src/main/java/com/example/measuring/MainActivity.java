package com.example.measuring;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    /*
    // 자이로/가속도 센서 사용
    private SensorManager mSensorManager = null;
    // 가속도 센서 사용
    private SensorEventListener mAccLis;
    private Sensor mAccelometerSensor = null;
    */
    private static String TAG = "MainActivity";
    JavaCameraView javaCameraView;
    private Mat matInput;
    private Mat matResult;
    private int cameraType = 0;  //1은 전방 type camera, 0은 후방 type camera
    Mat img, imgGray, imgCanny, imgHSV, threshold;
    int counter = 0;
    public native long loadCascade(String cascadeFileName);
    private final Semaphore writeLock = new Semaphore(1);

    public void getWriteLock() throws InterruptedException{
        writeLock.acquire();
    }

    public void releaseWriteLock() {
        writeLock.release();
    }

    static{
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }

    public void copyFile(String filename) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator +filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.i(TAG, "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.i(TAG, "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }
    }

    //출처: https://deepdeepit.tistory.com/30#recentEntries [Deep Deep IT]


    BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    javaCameraView.enableView();

                }break;
                default: {
                    super.onManagerConnected(status);

                }break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN); //모든 화면 장식(상태표시줄 등)이 창이 표시되는 동안 사라진다.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 사용자가 윈도우를 표시할 대 화면을 밝게 해준다.
        setContentView(R.layout.activity_main);



        /*
        // 가속도/자이로 센서 사용
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // 가속도 센서 사용
        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccLis = new AccelometerListener();
        //Touch Listener for Accelometer
        findViewById(R.id.accel_measure).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        mSensorManager.registerListener(mAccLis, mAccelometerSensor, SensorManager.SENSOR_DELAY_UI);
                        break;
                    case MotionEvent.ACTION_UP:
                        mSensorManager.unregisterListener(mAccLis);
                        break;
                }
                return false;
            }
        });
        */
        javaCameraView = (JavaCameraView) findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        javaCameraView.setCameraIndex(0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {
                //퍼미션 허가 안되어있다면 사용자에게 요청
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }

        Button button = (Button)findViewById(R.id.Capture);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try{
                    getWriteLock();

                    File path = new File(Environment.getExternalStorageDirectory() + "/Images/");
                    path.mkdirs();
                    File file  = new File(path, "image.png");

                    String filename = file.toString();

                    Imgproc.cvtColor(matResult, matResult, Imgproc.COLOR_BGR2RGB, 4);
                    boolean ret = Imgcodecs.imwrite( filename, matResult);
                    if ( ret ) Log.i(TAG, "SUCESS");
                    else Log.i(TAG, "FAIL");
                    Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(file));
                    sendBroadcast(mediaScanIntent);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                releaseWriteLock();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null)
            javaCameraView.disableView();
        /*
        Log.e("LOG", "onPause()");
        mSensorManager.unregisterListener(mAccLis);
        */
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null)
            javaCameraView.disableView();
        /*
        Log.e("LOG", "onDestroy()");
        mSensorManager.unregisterListener(mAccLis);
        */
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallBack);
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else {
            Log.i(TAG, "OpenCV not loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallBack);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        img = new Mat(height, width, CvType.CV_8UC4);
        imgGray = new Mat(height, width, CvType.CV_8UC1);
        imgCanny = new Mat(height, width, CvType.CV_8UC1);
        imgHSV = new Mat(height, width, CvType.CV_8UC4);
        threshold = new Mat(height, width, CvType.CV_8SC4);
    }

    @Override
    public void onCameraViewStopped() {
        img.release();
    }
    /*
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        double reference = 0, dimA = 0, dimB = 0;
        img = inputFrame.rgba();
        //Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGBA);
        Imgproc.cvtColor(img, imgGray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(imgGray, imgGray, new Size(7,7), 0);
        Imgproc.Canny(imgGray, imgCanny, 50, 100);
        // Best : (12, 12)
        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(4,4));
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(4,4));
        Imgproc.dilate(imgCanny, imgCanny, dilateElement);
        Imgproc.erode(imgCanny, imgCanny, erodeElement);
        List<MatOfPoint> cnts = new ArrayList<>();
        Imgproc.findContours(imgCanny, cnts, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        //Imgproc.drawContours(img, cnts, -1, new Scalar(255,255,255));
        for (MatOfPoint c : cnts)
            Imgproc.fillPoly(imgCanny, Arrays.asList(c), new Scalar(255,255,255));
        //List<Point> box = new ArrayList<Point>();
        for (int i=0; i<cnts.size(); i++) {
            if (Imgproc.contourArea(cnts.get(i)) > 130 && Imgproc.contourArea(cnts.get(i)) < 300) {
                //int maxId = cnts.indexOf(cnts);
                MatOfPoint maxMatOfPoint = cnts.get(i);
                MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                RotatedRect rect = Imgproc.minAreaRect(maxMatOfPoint2f);
                Point points[] = new Point[4];
                rect.points(points);
                for (int j=0; j<4; j++) {
                    Imgproc.line(img, points[j], points[(j + 1) % 4], new Scalar(0, 255, 0), 8);
                    Imgproc.circle(img, new Point(points[j].x, points[j].y), 5, new Scalar(0, 0, 255), 8);
                    Imgproc.circle(img, midPoint(points[j], points[(j+1)%4]), 5, new Scalar(255,0,0), 8);
                }
                for (int j=0; j<2; j++)
                    Imgproc.line(img, midPoint(points[j%4], points[(j+1)%4]), midPoint(points[(j+2)%4], points[(j+3)%4]), new Scalar(255,0,255), 8);
                double dA = euclidean(midPoint(points[0], points[1]), midPoint(points[2], points[3]));
                double dB = euclidean(midPoint(points[0], points[3]), midPoint(points[1], points[2]));
                if (i == 0 || reference == 0)
                    reference = dB / 2.4;
                dimA = dA / reference;
                dimB = dB / reference;
                Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimA)) + "cm", new Point(midPoint(points[0], points[1]).x - 15, midPoint(points[0], points[0]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255,255,255), 3);
                Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimB)) + "cm", new Point(midPoint(points[1], points[2]).x - 15, midPoint(points[1], points[2]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255,255,255), 3);
            }
        }
        return img;
    }
    */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        double reference = 0, dimA = 0, dimB = 0;
        img = inputFrame.rgba();
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(img, imgHSV, Imgproc.COLOR_BGR2HSV);
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGBA);
        // Best : (40,20,0) (180,255,255)
        // Secong: (30,0,0) (180,255,255)
        // Hue : 색상(색의 질)
        // Saturation : 채도(높아질수록 잡티 심해짐)
        // Value : 명도(밝기)
        Core.inRange(imgHSV, new Scalar(0,30,0), new Scalar(180,255,255), threshold);

        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5));
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5));

        Imgproc.erode(threshold, threshold, erodeElement);
        Imgproc.dilate(threshold, threshold, dilateElement);

        Imgproc.erode(threshold, threshold, erodeElement);
        Imgproc.dilate(threshold, threshold, dilateElement);


        Imgproc.dilate(threshold, threshold, dilateElement);
        Imgproc.erode(threshold, threshold, erodeElement);

        Imgproc.dilate(threshold, threshold, dilateElement);
        Imgproc.erode(threshold, threshold, erodeElement);


        List<MatOfPoint> cnts = new ArrayList<>();
        Imgproc.findContours(threshold, cnts, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        //Imgproc.drawContours(img, cnts, -1, new Scalar(255,255,255));
        for (MatOfPoint c : cnts)
            Imgproc.fillPoly(threshold, Arrays.asList(c), new Scalar(255,255,255));
        //List<Point> box = new ArrayList<Point>();

        for (int i=0; i<cnts.size(); i++) {
            if (Imgproc.contourArea(cnts.get(i)) > 200) {
                MatOfPoint maxMatOfPoint = cnts.get(i);
                MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                RotatedRect rect = Imgproc.minAreaRect(maxMatOfPoint2f);

                Point points[] = new Point[4];
                rect.points(points);
                for (int j = 0; j < 4; j++) {
                    Imgproc.line(img, points[j], points[(j + 1) % 4], new Scalar(0, 255, 0), 8);
                    Imgproc.circle(img, new Point(points[j].x, points[j].y), 5, new Scalar(0, 0, 255), 8);
                    Imgproc.circle(img, midPoint(points[j], points[(j + 1) % 4]), 5, new Scalar(255, 0, 0), 8);
                }
                for (int j = 0; j < 2; j++)
                    Imgproc.line(img, midPoint(points[j % 4], points[(j + 1) % 4]), midPoint(points[(j + 2) % 4], points[(j + 3) % 4]), new Scalar(255, 0, 255), 8);

                double dA = euclidean(midPoint(points[0], points[1]), midPoint(points[2], points[3]));
                double dB = euclidean(midPoint(points[0], points[3]), midPoint(points[1], points[2]));

                if (i == 0 || reference == 0)
                    reference = dB / 2.4;

                dimA = dA / reference;
                dimB = dB / reference;

                Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimA)) + "cm", new Point(midPoint(points[0], points[1]).x - 15, midPoint(points[0], points[0]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimB)) + "cm", new Point(midPoint(points[1], points[2]).x - 15, midPoint(points[1], points[2]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
            }
        }
        this.counter++;
        return img;
        //return threshold;
    }

    public Point midPoint(Point a, Point b) {
        return new Point((a.x + b.x) / 2, (a.y + b.y) / 2);
    }

    public double euclidean(Point a, Point b) {
        return Math.sqrt(square(a.x - b.x) + square(a.y - b.y));
    }

    public double square(double x) {
        return x * x;
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

    //여기서부턴 퍼미션 관련 메소드
    static final int PERMISSIONS_REQUEST_CODE = 1000;
    String[] PERMISSIONS  = {"android.permission.CAMERA"};

    private boolean hasPermissions(String[] permissions) {
        int result;
        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions){
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED){
                //허가 안된 퍼미션 발견
                return false;
            }
        }
        //모든 퍼미션이 허가되었음
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){
            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0]
                            == PackageManager.PERMISSION_GRANTED;

                    if (!cameraPermissionAccepted)
                        showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
                }
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

    private class AccelometerListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            double accX = event.values[0];
            double accY = event.values[1];
            double accZ = event.values[2];

            double angleXZ = Math.atan2(accX,  accZ) * 180/Math.PI;
            double angleYZ = Math.atan2(accY,  accZ) * 180/Math.PI;

            Log.e("LOG", "ACCELOMETER           [X]:" + String.format("%.4f", event.values[0])
                    + "           [Y]:" + String.format("%.4f", event.values[1])
                    + "           [Z]:" + String.format("%.4f", event.values[2])
                    + "           [angleXZ]: " + String.format("%.4f", angleXZ)
                    + "           [angleYZ]: " + String.format("%.4f", angleYZ));

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
}