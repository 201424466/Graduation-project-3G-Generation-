package com.example.measuring;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static String TAG = "MainActivity";
    JavaCameraView javaCameraView;
    Button EstimateButton;
    Mat img, imgGray, imgCanny, imgCnts, imgHSV, threshold, imgBinary, imgWarped, imgWarpedBinary;
    boolean estimate_flag = false;
    Point refPoint[] = new Point[4];

    List<Point> ref_point = new ArrayList<>();
    List<Point> warped_ref_point = new ArrayList<>();
    List<Point> warped_point;

////////////////////////////////////////////////////////////////////////////////////////////////////

    /** 자이로/가속도 센서 사용*/
    private SensorManager mSensorManager = null;
    private UserSensorListener userSensorListener;
    private Sensor mAccelometerSensor = null;
    private Sensor mGyroSensor = null;

    /** 센서 변수들 */
    private float[] mGyroValues = new float[3];
    private float[] mAccValues = new float[3];
    private double mAccPitch, mAccRoll;


    /** 보수 필터에 사용 */
    private float a = 0.2f;
    private static final float NS2S = 1.0f/1000000000.0f;
    private double pitch = 0, roll = 0, yaw = 0;
    private double timestamp, dt, temp, runing;
    private boolean gyroRunning, accRunning;

//////////////////////////////////////////////////////////////////////////////////////////////////

    BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    javaCameraView.enableView();
                    break;
                }
                default: {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    static { }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EstimateButton = (Button) this.findViewById(R.id.BtnEstimate);
        EstimateButton.setOnClickListener(click);

        javaCameraView = (JavaCameraView) findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {
                //퍼미션 허가 안되어있다면 사용자에게 요청
                requestPermissions(PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }

////////////////////////////////////////////////////////////////////////////////////////////////////

        /** 가속도/자이로 센서 사용 */
        mSensorManager = (SensorManager) getSystemService(Application.SENSOR_SERVICE);
        userSensorListener  = new UserSensorListener();
        mAccelometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

////////////////////////////////////////////////////////////////////////////////////////////////////
    }

    private View.OnClickListener click = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.BtnEstimate:
                    estimate_flag = !estimate_flag;
                    break;
                default :
                    break;
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        mSensorManager.unregisterListener(userSensorListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        mSensorManager.unregisterListener(userSensorListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else {
            Log.i(TAG, "OpenCV not loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallBack);
        }
        mSensorManager.registerListener(userSensorListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(userSensorListener, mAccelometerSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        img = new Mat(height, width, CvType.CV_8UC4);
        imgGray = new Mat(height, width, CvType.CV_8UC1);
        imgCanny = new Mat(height, width, CvType.CV_8UC1);
        imgCnts = new Mat(height, width, CvType.CV_8UC4);
        imgHSV = new Mat(height, width, CvType.CV_8UC4);
        imgBinary = new Mat(height, width, CvType.CV_8UC4);
        threshold = new Mat(height, width, CvType.CV_8SC4);
        imgWarped = new Mat(height, width, CvType.CV_8UC4);
        imgWarpedBinary = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        img.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        img = inputFrame.rgba();
        Mat img_result = new Mat();
        Mat copyImg = new Mat();
        Mat templateCopy = new Mat();

        img.copyTo(copyImg);
        Imgproc.cvtColor(copyImg, copyImg, Imgproc.COLOR_BGR2GRAY);
        copyImg.convertTo(copyImg, CvType.CV_32FC1);

        Mat imageUri = null;

        try {
            imageUri = Utils.loadResource(this, R.drawable.number0, Imgcodecs.IMREAD_COLOR);
            imageUri.copyTo(templateCopy);
            Imgproc.cvtColor(templateCopy, templateCopy, Imgproc.COLOR_BGR2GRAY);
            templateCopy.convertTo(templateCopy, CvType.CV_32FC1);

            String dir = null;
            File output = new File(dir + "/output" + Calendar.getInstance().getTime() + ".jpeg");
            run(copyImg, templateCopy, output.getAbsolutePath(), Imgproc.TM_CCORR_NORMED);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return img;

        //return estimate_size(img, estimate_flag);
        //return detect_poly(img);
        //return detect_rectangle(img, estimate_flag);
        //return img_segmentation(img, estimate_flag);
        //return watershed(img, estimate_flag);
        //return watershed_warp(img, estimate_flag);
    }

    public void run(Mat inFile, Mat templateFile, String outFile, int match_method){
        Log.e(TAG, "\nRunning Template Matching");

        Mat img_input = inFile;
        Mat templ = templateFile;

        //Create the result matrix
        int result_cols = img.cols() - templ.cols() + 1;
        Log.e(TAG,"result_cols : "+result_cols+"\nimg_cols : "+img.cols()+"\ntempl_cols : "+templ.cols());
        int result_rows = img.rows() - templ.rows() + 1;
        Log.e(TAG,"result_rows : "+result_cols+"\nimg_rows : "+img.rows()+"\ntempl_rows : "+templ.rows());
        Mat result = new Mat(result_rows, result_cols, CvType.CV_32FC1);
        Log.e(TAG,"final output mat rows : "+result.rows()+"\nfinal output mat cols : "+result.cols());

        //Do the Matching and Normalize
        Imgproc.matchTemplate(img, templ, result, match_method);
        Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());

        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

        Point matchLoc;
        if (match_method == Imgproc.TM_SQDIFF || match_method == Imgproc.TM_SQDIFF_NORMED) {
            matchLoc = mmr.minLoc;
        } else {
            matchLoc = mmr.maxLoc;
        }

        // / Show me what you got
        Imgproc.rectangle(img, matchLoc, new Point(matchLoc.x + templ.cols(),
                matchLoc.y + templ.rows()), new Scalar(0, 255, 0));

        // Save the visualized detection.
        Log.e(TAG,"Writing "+ outFile);
        if(estimate_flag)
            Imgcodecs.imwrite(outFile, img);

    }



    /* 물체 인식 & 길이 측정 */
    public Mat estimate_size(Mat img, boolean estimate_flag) {
        double reference = 0, dimA = 0, dimB = 0;

        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(img, imgHSV, Imgproc.COLOR_BGR2HSV);
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGBA);

        // Best : (0,30,0) (180,255,255)
        // Second: (40,20,0) (180,255,255)
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

        /* 레퍼런스(동전) 꼭지점 좌표 저장 */
        //Imgproc.drawContours(img, cnts, 0, new Scalar(255,255,255), 3);


        /* ESTIMATE 버튼 눌렀을 때 측정 시작 */
        if (estimate_flag) {
            for (int i = 0; i < cnts.size(); i++) {
                if (Imgproc.contourArea(cnts.get(i)) > 200) {
                    MatOfPoint maxMatOfPoint = cnts.get(i);
                    MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                    RotatedRect rect = Imgproc.minAreaRect(maxMatOfPoint2f);

                    Point points[] = new Point[4];
                    rect.points(points);
                    for (int j = 0; j < 4; j++) {
                        Imgproc.line(img, points[j], points[(j + 1) % 4], new Scalar(0, 255, 0), 8);
                        Imgproc.circle(img, new Point(points[j].x, points[j].y), 5, new Scalar(0, 0, 255), 8);
                        //Imgproc.circle(img, midPoint(points[j], points[(j + 1) % 4]), 5, new Scalar(255, 0, 0), 8);

                        Imgproc.line(imgHSV, points[j], points[(j + 1) % 4], new Scalar(0, 255, 0), 8);
                        Imgproc.circle(imgHSV, new Point(points[j].x, points[j].y), 5, new Scalar(0, 0, 255), 8);
                        //Imgproc.circle(imgHSV, midPoint(points[j], points[(j + 1) % 4]), 5, new Scalar(255, 0, 0), 8);
                    }
                    for (int j = 0; j < 2; j++) {
                        //Imgproc.line(img, midPoint(points[j % 4], points[(j + 1) % 4]), midPoint(points[(j + 2) % 4], points[(j + 3) % 4]), new Scalar(255, 0, 255), 8);

                        //Imgproc.line(imgHSV, midPoint(points[j % 4], points[(j + 1) % 4]), midPoint(points[(j + 2) % 4], points[(j + 3) % 4]), new Scalar(255, 0, 255), 8);
                    }

                    double dA = euclidean(midPoint(points[0], points[1]), midPoint(points[2], points[3]));
                    double dB = euclidean(midPoint(points[0], points[3]), midPoint(points[1], points[2]));

                    if (i == 0 || reference == 0)
                        reference = dB / 2.4;

                    dimA = dA / reference;
                    dimB = dB / reference;  //new Point(midPoint(points[0], points[1]).x - 15, midPoint(points[0], points[0]).y)

                    Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimA)) + "cm", new Point(midPoint(points[1], points[2]).x - 15, midPoint(points[1], points[2]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dimB)) + "cm", new Point(midPoint(points[0], points[1]).x, midPoint(points[0], points[0]).y - 180), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);

                    Imgproc.putText(imgHSV, Double.parseDouble(String.format("%.1f", dimA)) + "cm", new Point(midPoint(points[0], points[1]).x - 15, midPoint(points[0], points[0]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    Imgproc.putText(imgHSV, Double.parseDouble(String.format("%.1f", dimB)) + "cm", new Point(midPoint(points[1], points[2]).x - 15, midPoint(points[1], points[2]).y), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);

                }
            }
        }
        display_angle(img);

        return img;
    }

    public Mat detect_rectangle(Mat img, boolean estimate_flag) {
        Imgproc cv = new Imgproc();
        /*
        List<Point> ref_point = new ArrayList<>();
        List<Point> warped_ref_point = new ArrayList<>();
        List<Point> warped_point;
        */
        double reference = 0, dim1 = 0, dim2 = 0, dim3 = 0, dim4 = 0, ref_length1 = 0, ref_length2 = 0, ref_length3 = 0, ref_length4 = 0;

        if (estimate_flag) {
            /* 에지 이미지로 변환 */
            cv.cvtColor(img, img, cv.COLOR_RGBA2BGR);
            cv.cvtColor(img, imgGray, cv.COLOR_BGR2GRAY);
            cv.cvtColor(img, img, cv.COLOR_BGR2RGB);
            cv.GaussianBlur(imgGray, imgCanny, new Size(3, 3), 0);
            cv.Canny(imgCanny, imgCanny, 20, 200);

            /* 에지 이미지를 morphologyEx 함수로 형태를 뭉갬 */
            //Mat kernel = Imgproc.getStructuringElement(cv.MORPH_ELLIPSE, new Size(11, 11));
            Mat kernel = Imgproc.getStructuringElement(cv.MORPH_ELLIPSE, new Size(5,5 ));
            Mat morph = new Mat();
            cv.morphologyEx(imgCanny, morph, cv.MORPH_CLOSE, kernel);

            /* contours 함수를 이용해 Morph 이미지의 도형들을 그룹화, 그림 */
            int idx, i = 0, obj_num = 0;
            List<MatOfPoint> cnts = new ArrayList<>();
            cv.findContours(morph, cnts, new Mat(), cv.RETR_CCOMP, cv.CHAIN_APPROX_SIMPLE, new Point(0, 0));


            Mat imgCnts = new Mat();
            cv.cvtColor(imgCanny, imgCnts, cv.COLOR_GRAY2BGR);

            for (idx = 0; idx < cnts.size(); idx++) {
                MatOfPoint maxMatOfPoint = cnts.get(i);
                MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                RotatedRect rect = cv.minAreaRect(maxMatOfPoint2f);
                //double areaRatio = Math.abs(cv.contourArea(cnts.get(i))) / (rect.size.width * rect.size.height);
                cv.drawContours(imgCnts, cnts, idx, new Scalar(255, 0, 0), cv.FILLED);
            }
            cv.cvtColor(imgCnts, imgCnts, cv.COLOR_BGR2RGB);


            /* contours 이미지를 approxPolyDP를 이용해 선분 간략화 */
            MatOfPoint2f poly2f = new MatOfPoint2f();
            //Mat imgRect = new Mat();
            List<Point> poly_point = new ArrayList<>();
            //cv.cvtColor(imgCanny, imgRect, cv.COLOR_GRAY2BGR);

            for (idx = 0; idx < cnts.size(); idx++) {
                if (cv.contourArea(cnts.get(idx)) > 100) {
                    if (obj_num >= 5)
                        break;
                    MatOfPoint maxMatOfPoint = cnts.get(idx);
                    MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                    double epsilon1 = 0.01 * cv.arcLength(maxMatOfPoint2f, true);
                    double epsilon2 = 0.1 * cv.arcLength(maxMatOfPoint2f, true);
                    cv.approxPolyDP(maxMatOfPoint2f, poly2f, epsilon1, true);

                    poly_point = poly2f.toList();
                    for (i = 0; i < poly_point.size(); i++) {
                        if (poly_point.size() == 4) {
                            cv.line(img, poly_point.get(i), poly_point.get((i + 1) % poly_point.size()), new Scalar(0, 0, 255), 5);
                            cv.circle(img, new Point(poly_point.get(i).x, poly_point.get(i).y), 5, new Scalar(0, 0, 255), 8);
                            // 꼭지점 좌표 표시
                            //cv.putText(img, pointStr, poly_point.get(i), cv.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                        }
                    }
                    // 사각형인 물체만 인식
                    if (poly_point.size() == 4) {
                        double d1 = 0, d2 = 0, d3 = 0, d4 = 0;

                        //동전 크기의 정사각형을 레퍼런스로 설정
                        if (idx == 0 || reference == 0) {
                            ref_point = new ArrayList<Point>();
                            warped_ref_point = new ArrayList<Point>();

                            // 화면에 표시될 실제 레퍼런스의 길이 (real ref = 2.4)
                            dim1 = 2.4;
                            dim2 = 2.4;
                            dim3 = 2.4;
                            dim4 = 2.4;
                            reference = dim1 / 2.4;

                            /*
                            d1 = euclidean(poly_point.get(0), poly_point.get(1));
                            reference = d1 / 2.4;
                            dim1 = d1 / reference;
                            dim2 = dim1;
                            */

                            // 화면 상에 나타난 레퍼런스의 꼭지점 저장
                            for (int j=0; j<4; j++)
                                ref_point.add(new Point(poly_point.get(j).x, poly_point.get(j).y));

                            // 화면 상에 나타난 레퍼런스의 길이 (fake ref)
                            ref_length2 = euclidean(ref_point.get(0), ref_point.get(1));
                            ref_length1 = euclidean(ref_point.get(1), ref_point.get(2));
                            //ref_length3 = euclidean(ref_point.get(2), ref_point.get(3));
                            //ref_length4 = euclidean(ref_point.get(3), ref_point.get(0));

                            // 레퍼런스를 240*240 크기로 와핑
                            warped_ref_point.add(new Point(0, 0));
                            warped_ref_point.add(new Point(0, 240));
                            warped_ref_point.add(new Point(240, 0));

                        }
                        // 레퍼런스가 아닌 물체 길이 측정
                        else {
                            warped_point = new ArrayList<Point>();

                            // 화면 상의 물체의 길이 (fake obj)
                            d2 = euclidean(poly_point.get(0), poly_point.get(1));
                            d1 = euclidean(poly_point.get(1), poly_point.get(2));
                            //d3 = euclidean(poly_point.get(2), poly_point.get(3));
                            //d4 = euclidean(poly_point.get(3), poly_point.get(0));

                            /*
                            d1 = d1 / reference;
                            d2 = d1 / reference;
                            */

                            // 인식한 물제를 레퍼런스와의 비율에 맞게 와핑
                            warped_point.add(new Point(0, 0));
                            warped_point.add(new Point(240 * (d1 / ref_length1) , 0));
                            warped_point.add(new Point(0, 240 * (d2 / ref_length2)));

                            // 실제 물체의 길이 (real obj)
                            dim1 = 2.4 * (d1 / ref_length1);
                            dim2 = 2.4 * (d2 / ref_length2);
                            //dim3 = 2.4 * (d3 / ref_length3);
                            //dim4 = 2.4 * (d4 / ref_length4);

                            /*
                            dim1 = d1 / reference;
                            dim2 = d2 / reference;
                            */

                        }

                        Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim2)) + "cm", midPoint(poly_point.get(0), poly_point.get(1)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                        Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim1)) + "cm", midPoint(poly_point.get(1), poly_point.get(2)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                        //Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim3)) + "cm", midPoint(poly_point.get(2), poly_point.get(3)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                        //Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim4)) + "cm", midPoint(poly_point.get(3), poly_point.get(0)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);

                    }
                    //obj_num++;
                }
            }

        }
        display_angle(img);

        return img;
    }

    public Mat img_segmentation(Mat img, boolean estimate_flag) {
        Imgproc cv = new Imgproc();
        List<Point> ref_point = new ArrayList<>();
        List<Point> warped_ref_point = new ArrayList<>();
        List<Point> warped_point;
        double reference = 0, dim1 = 0, dim2 = 0, ref_length1 = 0, ref_length2 = 0;

        Mat img_gray_blur = new Mat();
        Mat morph = new Mat();

        if (true) {
            /* 에지 이미지로 변환 */
            cv.cvtColor(img, img, cv.COLOR_RGBA2BGR);
            cv.cvtColor(img, imgGray, cv.COLOR_BGR2GRAY);
            cv.cvtColor(img, img, cv.COLOR_BGR2RGB);
////////////////////////////////////////////////////////////////////////////////////////////////////
            // 인식 성능 향상을 위해 추가된 부분

            cv.GaussianBlur(imgGray, img_gray_blur, new Size(15,15), 0);
            cv.adaptiveThreshold(img_gray_blur, threshold, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY_INV, 11, 1);

////////////////////////////////////////////////////////////////////////////////////////////////////
            //cv.Canny(imgCanny, imgCanny, 20, 200);

            // 에지 이미지를 morphologyEx 함수로 형태를 뭉갬
            //Mat kernel = Imgproc.getStructuringElement(cv.MORPH_ELLIPSE, new Size(11, 11));
            Mat kernel = Imgproc.getStructuringElement(cv.MORPH_ELLIPSE, new Size(7,7));
            cv.morphologyEx(threshold, morph, cv.MORPH_OPEN, kernel);

////////////////////////////////////////////////////////////////////////////////////////////////////
            // 인식 성능 향상을 위해 추가된 부분


            int idx, i = 0, obj_num = 0;
            List<MatOfPoint> cnts = new ArrayList<>();
            cv.findContours(morph, cnts, new Mat(), cv.RETR_CCOMP, cv.CHAIN_APPROX_SIMPLE, new Point(0, 0));

            MatOfPoint2f poly2f = new MatOfPoint2f();
            List<Point> poly_point = new ArrayList<>();

            for (idx = 0; idx < cnts.size(); idx++) {
                if (cv.contourArea(cnts.get(idx)) > 100) {
                    if (obj_num >= 5)
                        break;
                    MatOfPoint maxMatOfPoint = cnts.get(idx);
                    MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                    double epsilon1 = 0.01 * cv.arcLength(maxMatOfPoint2f, true);
                    double epsilon2 = 0.1 * cv.arcLength(maxMatOfPoint2f, true);
                    cv.approxPolyDP(maxMatOfPoint2f, poly2f, epsilon1, true);

                    poly_point = poly2f.toList();
                    for (i = 0; i < poly_point.size(); i++) {
                        if (poly_point.size() == 4) {
                            cv.line(img, poly_point.get(i), poly_point.get((i + 1) % poly_point.size()), new Scalar(0, 0, 255), 5);
                            cv.circle(img, new Point(poly_point.get(i).x, poly_point.get(i).y), 5, new Scalar(0, 0, 255), 8);
                            // 꼭지점 좌표 표시
                            //cv.putText(img, pointStr, poly_point.get(i), cv.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                        }
                    }
                    // 사각형인 물체만 인식
                    if (poly_point.size() == 4) {
                        double d1 = 0, d2 = 0;

                        //동전 크기의 정사각형을 레퍼런스로 설정
                        if (idx == 0 || reference == 0) {
                            d1 = euclidean(poly_point.get(0), poly_point.get(1));
                            reference = d1 / 2.4;
                            dim1 = d1 / reference;
                            dim2 = dim1;


                            for (int j=0; j<4; j++)
                                ref_point.add(new Point(poly_point.get(j).x, poly_point.get(j).y));

                            ref_length1 = euclidean(ref_point.get(0), ref_point.get(1));
                            ref_length2 = euclidean(ref_point.get(1), ref_point.get(2));

                            // 레퍼런스를 240*240 크기로 와핑
                            warped_ref_point.add(new Point(0, 0));
                            warped_ref_point.add(new Point(0, 240));
                            warped_ref_point.add(new Point(240, 0));

                        }
                        // 레퍼런스가 아닌 물체 길이 측정
                        else {
                            warped_point = new ArrayList<Point>();
                            d1 = euclidean(poly_point.get(0), poly_point.get(1));
                            d2 = euclidean(poly_point.get(1), poly_point.get(2));

                            // 인식한 물제를 레퍼런스와의 비율에 맞게 와핑
                            warped_point.add(new Point(0, 0));
                            warped_point.add(new Point(240 * (d1 / ref_length1), 0));
                            warped_point.add(new Point(0, 240 * (d2 / ref_length2)));

                            //dim1 = d1 / reference;
                            //dim2 = d2 / reference;
                            dim1 = euclidean(warped_point.get(0), warped_point.get(1)) / 100;
                            dim2 = euclidean(warped_point.get(1), warped_point.get(2)) / 100;
                        }

                        Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim1)) + "cm", midPoint(poly_point.get(0), poly_point.get(1)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                        Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim2)) + "cm", midPoint(poly_point.get(1), poly_point.get(2)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    }
                    //obj_num++;
                }
            }

        }
        display_angle(img);

        return img;
    }

    public Mat watershed_warp(Mat img, boolean estimate_flag) {
        Imgproc cv = new Imgproc();
        Mat fg = new Mat();
        Mat bg = new Mat();
        Mat result = new Mat();
        Point[] srcPtn = new Point[4];
        Point[] dstPtn = new Point[4];
        int idx=0, i=0;
        double reference = 0, dim1 = 0, dim2 = 0, ref_length1 = 0, ref_length2 = 0;
        boolean findRef = false;

        cv.cvtColor(img, imgGray, cv.COLOR_BGR2GRAY);
        cv.cvtColor(img, img, cv.COLOR_RGBA2RGB);
        cv.threshold(imgGray, imgBinary, 109, 255, cv.THRESH_BINARY_INV);

        cv.erode(imgBinary, fg, new Mat(), new Point(-1, -1), 9);

        cv.dilate(imgBinary, bg, new Mat(), new Point(-1, -1), 20);
        cv.threshold(bg, bg, 1, 120, cv.THRESH_BINARY_INV);

        Mat markers = new Mat(imgBinary.size(), CvType.CV_8U, new Scalar(0));
        Core.add(fg, bg, markers);

        WatershedSegment segmenter = new WatershedSegment();
        segmenter.setMarkers(markers);

        segmenter.process(img);
        result = segmenter.getSegmentation();

        Mat markers2 = new Mat();

        // 이진화 이미지에서 경계선 검출 시작
        List<MatOfPoint> cnts = new ArrayList<>();
        cv.threshold(result, markers2, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU);
        cv.findContours(markers2, cnts, new Mat(), cv.RETR_LIST, cv.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        cv.drawContours(markers2, cnts, -1, new Scalar(255, 255, 255), cv.FILLED);
        // 경계선 검출 끝

        // contours 이미지를 approxPolyDP를 이용해 선분 간략화
        MatOfPoint2f poly2f = new MatOfPoint2f();
        List<Point> poly_point = new ArrayList<>();

        // 맨 왼쪽이 레퍼런스가 되도록 정렬
        // **정렬하는 코드**

        // 레퍼런스의 경계선 먼저 탐색
        if (cv.contourArea(cnts.get(0)) > 90) {
            MatOfPoint maxMatOfPoint = cnts.get(0);
            MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
            double epsilon1 = 0.01 * cv.arcLength(maxMatOfPoint2f, true);
            double epsilon2 = 0.1 * cv.arcLength(maxMatOfPoint2f, true);
            cv.approxPolyDP(maxMatOfPoint2f, poly2f, epsilon1, true);

            // 레퍼런스 주위에 사각형 그림
            poly_point = poly2f.toList();
            for (i = 0; i < poly_point.size(); i++) {
                if (poly_point.size() == 4) {
                    //cv.line(img, poly_point.get(i), poly_point.get((i + 1) % poly_point.size()), new Scalar(0, 0, 255), 5);
                    //cv.circle(img, new Point(poly_point.get(i).x, poly_point.get(i).y), 5, new Scalar(0, 0, 255), 8);
                    // 꼭지점 좌표 표시
                    //cv.putText(img, pointStr, poly_point.get(i), cv.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                }
            }

            if (poly_point.size() == 4) {
                // 레퍼런스를 기준으로 binary 이미지 전체 와핑
                // 와핑 전 레퍼런스 좌표
                srcPtn[0] = poly_point.get(0);
                srcPtn[1] = poly_point.get(1);
                srcPtn[2] = poly_point.get(2);
                srcPtn[3] = poly_point.get(3);

                // 와핑 후 레퍼런스 좌표 (정사각형)
                dstPtn[0] = new Point(400, 200);
                dstPtn[1] = new Point(400, 350);
                dstPtn[2] = new Point(250, 350);
                dstPtn[3] = new Point(250,200);

                Mat warpMat = cv.getPerspectiveTransform(new MatOfPoint2f(srcPtn), new MatOfPoint2f(dstPtn));
                // 와핑 성공
                cv.warpPerspective(markers2, imgWarpedBinary, warpMat, imgWarped.size());
                cv.warpPerspective(img, imgWarped, warpMat, imgWarpedBinary.size());
            }
        }
        // 레퍼런스 경계선 검출 & 와핑 끝

        // 와핑된 이진화 이미지에서 다시 경계선 검출
        cnts = new ArrayList<>();

        cv.threshold(imgWarpedBinary, markers2, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU);
        cv.findContours(markers2, cnts, new Mat(), cv.RETR_LIST, cv.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        cv.drawContours(markers2, cnts, -1, new Scalar(255, 255, 255), cv.FILLED);
        // 경계선 검출 끝

        // contours 이미지를 approxPolyDP를 이용해 선분 간략화
        poly2f = new MatOfPoint2f();
        poly_point = new ArrayList<>();

        // 맨 왼쪽이 레퍼런스가 되도록 정렬
        // **정렬하는 코드**

        for (idx = 0; idx < cnts.size(); idx++) {
            if (cv.contourArea(cnts.get(idx)) > 90) {
                MatOfPoint maxMatOfPoint = cnts.get(idx);
                MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                double epsilon1 = 0.01 * cv.arcLength(maxMatOfPoint2f, true);
                double epsilon2 = 0.1 * cv.arcLength(maxMatOfPoint2f, true);
                cv.approxPolyDP(maxMatOfPoint2f, poly2f, epsilon1, true);
            }

            poly_point = poly2f.toList();

            for (i = 0; i < poly_point.size(); i++) {
                if (poly_point.size() == 4) {
                    cv.line(imgWarped, poly_point.get(i), poly_point.get((i + 1) % poly_point.size()), new Scalar(0,0,255), 5);
                    cv.circle(imgWarped, new Point(poly_point.get(i).x, poly_point.get(i).y), 5, new Scalar(0,0,255), 8);
                    // 꼭지점 좌표 표시
                    //cv.putText(img, pointStr, poly_point.get(i), cv.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                }
            }

            // 사각형인 물체만 인식
            if (poly_point.size() == 4) {
                double d1 = 0, d2 = 0, d3 = 0, d4 = 0;
                ref_point = new ArrayList<Point>();

                //동전 크기의 정사각형을 레퍼런스로 설정
                if (idx == 0) {
                    ref_point = new ArrayList<Point>();

                    // 화면에 표시될 실제 레퍼런스의 길이 (real ref = 2.4)
                    dim1 = 2.4;
                    dim2 = 2.4;
                    //dim3 = 2.4;
                    //dim4 = 2.4;
                    reference = dim1 / 2.4;

                    for (int j=0; j<poly_point.size(); j++) {
                        ref_point.add(poly_point.get(j));
                    }

                    //
                    d1 = euclidean(poly_point.get(0), poly_point.get(1));
                    reference = d1 / 2.4;
                    dim1 = d1 / reference;
                    dim2 = dim1;

                    // 화면 상에 나타난 레퍼런스의 길이 (fake ref)
                    ref_length2 = euclidean(ref_point.get(0), ref_point.get(1));
                    ref_length1 = euclidean(ref_point.get(1), ref_point.get(2));
                    //ref_length3 = euclidean(ref_point.get(2), ref_point.get(3));
                    //ref_length4 = euclidean(ref_point.get(3), ref_point.get(0));
                }
                // 레퍼런스가 아닌 물체 길이 측정
                else {
                    // 화면 상의 물체의 길이 (fake obj)
                    d2 = euclidean(poly_point.get(0), poly_point.get(1));
                    d1 = euclidean(poly_point.get(1), poly_point.get(2));

                    // 실제 물체의 길이 (real obj)
                    dim1 = 2.4 * (d1 / ref_length1);
                    dim2 = 2.4 * (d2 / ref_length2);
                }

                Imgproc.putText(imgWarped, Double.parseDouble(String.format("%.1f", dim2)) + "cm", midPoint(poly_point.get(0), poly_point.get(1)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                Imgproc.putText(imgWarped, Double.parseDouble(String.format("%.1f", dim1)) + "cm", midPoint(poly_point.get(1), poly_point.get(2)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                //Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim3)) + "cm", midPoint(poly_point.get(2), poly_point.get(3)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                //Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim4)) + "cm", midPoint(poly_point.get(3), poly_point.get(0)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
            }
        }

        // 원래 사이즈로 다시 와핑
        if (ref_point.size() == 4) {
            srcPtn[0] = new Point(400, 200);
            srcPtn[1] = new Point(400, 350);
            srcPtn[2] = new Point(250, 350);
            srcPtn[3] = new Point(250, 200);

            dstPtn[0] = ref_point.get(0);
            dstPtn[1] = ref_point.get(1);
            dstPtn[2] = ref_point.get(2);
            dstPtn[3] = ref_point.get(3);

            Mat warpMat = cv.getPerspectiveTransform(new MatOfPoint2f(srcPtn), new MatOfPoint2f(dstPtn));
            // 와핑 성공
            cv.warpPerspective(imgWarped, img, warpMat, img.size());
        }
        return img;

    }

    public Mat watershed(Mat img, boolean estimate_flag) {
        Imgproc cv = new Imgproc();
        Mat fg = new Mat();
        Mat bg = new Mat();
        Mat result = new Mat();
        Point[] srcPtn = new Point[3];
        Point[] dstPtn = new Point[3];
        int idx, i;

        List<Point> ref_point = new ArrayList<>();
        List<Point> warped_ref_point = new ArrayList<>();
        List<Point> warped_point;
        double reference = 0, dim1 = 0, dim2 = 0, ref_length1 = 0, ref_length2 = 0;

        cv.cvtColor(img, imgGray, cv.COLOR_BGR2GRAY);
        cv.cvtColor(img, img, cv.COLOR_RGBA2RGB);
        cv.threshold(imgGray, imgBinary, 109, 255, cv.THRESH_BINARY_INV);

        cv.erode(imgBinary, fg, new Mat(), new Point(-1,-1), 5);

        cv.dilate(imgBinary, bg, new Mat(), new Point(-1,-1), 5);
        cv.threshold(bg, bg, 1, 120, cv.THRESH_BINARY_INV);

        Mat markers = new Mat(imgBinary.size(), CvType.CV_8U, new Scalar(0));
        Core.add(fg, bg, markers);

        WatershedSegment segmenter = new WatershedSegment();
        segmenter.setMarkers(markers);

        segmenter.process(img);
        result = segmenter.getSegmentation();
        //Core.subtract(result, bg, result);

        Mat markers2 = new Mat();
        List<MatOfPoint> cnts = new ArrayList<>();
        cv.threshold(result, markers2, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU);
        cv.findContours(markers2, cnts, new Mat(), cv.RETR_LIST, cv.CHAIN_APPROX_SIMPLE, new Point(0,0));
        cv.drawContours(markers2, cnts, -1, new Scalar(255,255,255), cv.FILLED);

        /*
        for (i = 0; i < cnts.size(); i++) {
            MatOfPoint maxMatOfPoint = cnts.get(i);
            MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
            RotatedRect rect = cv.minAreaRect(maxMatOfPoint2f);
            //double areaRatio = Math.abs(cv.contourArea(cnts.get(i))) / (rect.size.width * rect.size.height);
            cv.drawContours(markers2, cnts, i, new Scalar(0,0,0), cv.FILLED);
        }
        */

        // contours 이미지를 approxPolyDP를 이용해 선분 간략화
        MatOfPoint2f poly2f = new MatOfPoint2f();
        List<Point> poly_point = new ArrayList<>();


        for (idx = 0; idx < cnts.size(); idx++) {
            if ((cv.contourArea(cnts.get(idx)) > 90)) {
                MatOfPoint maxMatOfPoint = cnts.get(idx);
                MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                double epsilon1 = 0.01 * cv.arcLength(maxMatOfPoint2f, true);
                double epsilon2 = 0.1 * cv.arcLength(maxMatOfPoint2f, true);
                double epsilon3 = 0.05 * cv.arcLength(maxMatOfPoint2f, true);
                cv.approxPolyDP(maxMatOfPoint2f, poly2f, epsilon2, true);

                poly_point = poly2f.toList();
                for (i = 0; i < poly_point.size(); i++) {
                    if (poly_point.size() == 4) {
                        cv.line(img, poly_point.get(i), poly_point.get((i + 1) % poly_point.size()), new Scalar(0, 0, 255), 5);
                        cv.circle(img, new Point(poly_point.get(i).x, poly_point.get(i).y), 5, new Scalar(0, 0, 255), 8);
                        // 꼭지점 좌표 표시
                        //cv.putText(img, pointStr, poly_point.get(i), cv.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    }
                }


                // 사각형인 물체만 인식
                if (poly_point.size() == 4) {
                    double d1 = 0, d2 = 0, d3 = 0, d4 = 0;

                    //동전 크기의 정사각형을 레퍼런스로 설정
                    if (idx == 0 || reference == 0) {
                        ref_point = new ArrayList<Point>();
                        warped_ref_point = new ArrayList<Point>();

                        // 화면에 표시될 실제 레퍼런스의 길이 (real ref = 2.4)
                        dim1 = 2.4;
                        dim2 = 2.4;
                        //dim3 = 2.4;
                        //dim4 = 2.4;
                        reference = dim1 / 2.4;

                        //
                        d1 = euclidean(poly_point.get(0), poly_point.get(1));
                        reference = d1 / 2.4;
                        dim1 = d1 / reference;
                        dim2 = dim1;


                        // 화면 상에 나타난 레퍼런스의 꼭지점 저장
                        for (int j = 0; j < 4; j++)
                            ref_point.add(new Point(poly_point.get(j).x, poly_point.get(j).y));

                        // 화면 상에 나타난 레퍼런스의 길이 (fake ref)
                        ref_length2 = euclidean(ref_point.get(0), ref_point.get(1));
                        ref_length1 = euclidean(ref_point.get(1), ref_point.get(2));
                        //ref_length3 = euclidean(ref_point.get(2), ref_point.get(3));
                        //ref_length4 = euclidean(ref_point.get(3), ref_point.get(0));

                        // 레퍼런스를 240*240 크기로 와핑
                        warped_ref_point.add(new Point(0, 0));
                        warped_ref_point.add(new Point(0, 240));
                        warped_ref_point.add(new Point(240, 0));

                        srcPtn[0] = ref_point.get(0);
                        srcPtn[1] = ref_point.get(1);
                        srcPtn[2] = ref_point.get(2);

                        dstPtn[0] = new Point(200,50);
                        dstPtn[1] = new Point(200,150);
                        dstPtn[2] = new Point(100,150);

                        Mat warpMat = cv.getAffineTransform(new MatOfPoint2f(srcPtn), new MatOfPoint2f(dstPtn));

                        imgWarped = Mat.zeros(img.rows(), img.cols(), img.type());

                        cv.warpAffine(img, imgWarped, warpMat, imgWarped.size());
                    }
                    // 레퍼런스가 아닌 물체 길이 측정
                    else {
                        warped_point = new ArrayList<Point>();


                        // 화면 상의 물체의 길이 (fake obj)
                        d2 = euclidean(poly_point.get(0), poly_point.get(1));
                        d1 = euclidean(poly_point.get(1), poly_point.get(2));

                        // 인식한 물제를 레퍼런스와의 비율에 맞게 와핑
                        warped_point.add(new Point(0, 0));
                        warped_point.add(new Point(240 * (d1 / ref_length1), 0));
                        warped_point.add(new Point(0, 240 * (d2 / ref_length2)));

                        // 실제 물체의 길이 (real obj)
                        dim1 = 2.4 * (d1 / ref_length1);
                        dim2 = 2.4 * (d2 / ref_length2);
                    }

                    Imgproc.putText(imgWarped, Double.parseDouble(String.format("%.1f", dim2)) + "cm", midPoint(poly_point.get(0), poly_point.get(1)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    Imgproc.putText(imgWarped, Double.parseDouble(String.format("%.1f", dim1)) + "cm", midPoint(poly_point.get(1), poly_point.get(2)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    //Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim3)) + "cm", midPoint(poly_point.get(2), poly_point.get(3)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
                    //Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim4)) + "cm", midPoint(poly_point.get(3), poly_point.get(0)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);

                }

                //obj_num++;
            }

        }



        return img;
    }

    public Mat warp_image(Mat img) {
        Mat warped = new Mat();



        return warped;
    }

    public List<MatOfPoint> sortContours(List<MatOfPoint> cnts) {
        Imgproc cv = new Imgproc();
        List<MatOfPoint> sorted = new ArrayList<>();
        MatOfPoint2f poly2f = new MatOfPoint2f();
        List<Point> poly_point = new ArrayList<>();
        int idx, i;

        for (idx = 0; idx < cnts.size(); idx++) {
            if (cv.contourArea(cnts.get(idx)) > 100) {
                MatOfPoint maxMatOfPoint = cnts.get(idx);
                MatOfPoint2f maxMatOfPoint2f = new MatOfPoint2f(maxMatOfPoint.toArray());
                double epsilon1 = 0.01 * cv.arcLength(maxMatOfPoint2f, true);
                double epsilon2 = 0.1 * cv.arcLength(maxMatOfPoint2f, true);
                cv.approxPolyDP(maxMatOfPoint2f, poly2f, epsilon1, true);

                poly_point = poly2f.toList();

                if (poly_point.size() == 4) {
                    if (sorted.get(0) == null) {}

                }
            }
        }

        return sorted;
    }

    public void calculate_size(Mat img, int idx, double reference, List<Point> poly_point) {
        double d1 = 0, d2 = 0, dim1 = 0, dim2 = 0, ref_length1 = 0, ref_length2 = 0;

        //동전 크기의 정사각형을 레퍼런스로 설정
        if (idx == 0 || reference == 0) {
            d1 = euclidean(poly_point.get(0), poly_point.get(1));
            reference = d1 / 2.4;
            dim1 = d1 / reference;
            dim2 = dim1;


            for (int j=0; j<4; j++)
                ref_point.add(new Point(poly_point.get(j).x, poly_point.get(j).y));

            ref_length1 = euclidean(ref_point.get(0), ref_point.get(1));
            ref_length2 = euclidean(ref_point.get(1), ref_point.get(2));

            // 레퍼런스를 240*240 크기로 와핑
            warped_ref_point.add(new Point(0, 0));
            warped_ref_point.add(new Point(0, 240));
            warped_ref_point.add(new Point(240, 0));

            Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim1)) + "cm", midPoint(poly_point.get(0), poly_point.get(1)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
            Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim2)) + "cm", midPoint(poly_point.get(1), poly_point.get(2)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);


        }
        // 레퍼런스가 아닌 물체 길이 측정
        else {
            warped_point = new ArrayList<Point>();
            d1 = euclidean(poly_point.get(0), poly_point.get(1));
            d2 = euclidean(poly_point.get(1), poly_point.get(2));

            // 인식한 물제를 레퍼런스와의 비율에 맞게 와핑
            warped_point.add(new Point(0, 0));
            warped_point.add(new Point(240 * (d1 / ref_length1), 0));
            warped_point.add(new Point(0, 240 * (d2 / ref_length2)));

            //dim1 = d1 / reference;
            //dim2 = d2 / reference;
            dim1 = euclidean(warped_point.get(0), warped_point.get(1)) / 100;
            dim2 = euclidean(warped_point.get(1), warped_point.get(2)) / 100;

            Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim1)) + "cm", midPoint(poly_point.get(0), poly_point.get(1)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);
            Imgproc.putText(img, Double.parseDouble(String.format("%.1f", dim2)) + "cm", midPoint(poly_point.get(1), poly_point.get(2)), Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255, 255, 255), 3);

        }

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

    /** 퍼미션 관련 메소드 */

    static final int PERMISSIONS_REQUEST_CODE = 1000;
    String[] PERMISSIONS  = {"android.permission.CAMERA"};

    private boolean hasPermissions(String[] permissions) {
        int result;
        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions){
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED){
                // 허가 안된 퍼미션 발견
                return false;
            }
        }
        // 모든 퍼미션 허가됨
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

    /** 자이로 / 가속도 센서 */

    /** 1차 상보필터 적용 */
    private void complementaty(double new_ts){
        /** 자이로랑 가속 해제 */
        gyroRunning = false;
        accRunning = false;

        /** 센서 값 첫 출력시 dt(=timestamp - event.timestamp)에 오차가 생기므로 처음엔 break */
        if(timestamp == 0){
            timestamp = new_ts;
            return;
        }
        dt = (new_ts - timestamp) * NS2S; // ns->s 변환
        timestamp = new_ts;

        /** 가속도 센서를 읽어서 각도 계산 */
        mAccPitch = -Math.atan2(mAccValues[0], mAccValues[2]) * 180.0 / Math.PI; // Y 축 기준
        mAccRoll= Math.atan2(mAccValues[1], mAccValues[2]) * 180.0 / Math.PI; // X 축 기준

        /**
         * 1st complementary filter.
         *  mGyroValuess : 각속도 성분.
         *  mAccPitch : 가속도계를 통해 얻어낸 회전각.
         */
        temp = (1/a) * (mAccPitch - pitch) + mGyroValues[1];
        pitch = pitch + (temp*dt);

        temp = (1/a) * (mAccRoll - roll) + mGyroValues[0];
        roll = roll + (temp*dt);
    }

    public class UserSensorListener implements SensorEventListener{
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()){
                /** GYROSCOPE */
                case Sensor.TYPE_GYROSCOPE:
                    /** 센서 값을 mGyroValues에 저장 */
                    mGyroValues = event.values;
                    if(!gyroRunning)
                        gyroRunning = true;
                    break;

                /** ACCELEROMETER */
                case Sensor.TYPE_ACCELEROMETER:
                    /** 센서 값을 mAccValues에 저장 */
                    mAccValues = event.values;
                    if(!accRunning)
                        accRunning = true;
                    break;
            }

            /** 두 센서 새로운 값을 받으면 상보필터 적용 */
            if(gyroRunning && accRunning){
                complementaty(event.timestamp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }

    public void display_angle(Mat img) {
        /* 화면에 기울기 표시되는 위치 좌표 */

        Point rollPoint = new Point();
        Point pitchPoint = new Point();

        pitchPoint.x = 500;
        pitchPoint.y = 40;

        rollPoint.x = 500;
        rollPoint.y = 80;

        String pitchStr = String.format("%.1f", pitch + 89.2);
        String rollStr = String.format("%.1f", roll + 0.8);

        Imgproc.putText(img, "Front/Back : " + pitchStr + "`", pitchPoint, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255,255,255), 3);
        Imgproc.putText(img, "Right/Left : " + rollStr + "`", rollPoint, Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(255,255,255), 3);
    }
}
