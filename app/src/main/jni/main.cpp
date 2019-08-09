#include <jni.h>
#include "com_example_measuring_MainActivity.h"
#include <opencv2/opencv.hpp>
#include <android/log.h>

using namespace cv;
using namespace std;

extern "C"{

    float resize(Mat img_src, Mat &img_resize, int resize_width){
        float scale = resize_width / (float)img_src.cols ;
        if (img_src.cols > resize_width) {
         int new_height = cvRound(img_src.rows * scale);
            resize(img_src, img_resize, Size(resize_width, new_height));
     }
        else {
            img_resize = img_src;
        }
        return scale;
    }


    JNIEXPORT void JNICALL
   Java_com_tistory_webnautes_useopencvwithndk_1build_MainActivity_ConvertRGBtoGray(
            JNIEnv *env,
            jobject  instance,
            jlong matAddrInput,
            jlong matAddrResult){


        Mat &matInput = *(Mat *)matAddrInput;
        Mat &matResult = *(Mat *)matAddrResult;

        cvtColor(matInput, matResult, COLOR_RGBA2GRAY);


      }
}extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_measuring_MainActivity_loadCascade(JNIEnv *env, jobject instance,
                                                    jstring cascadeFileName_) {
    const char *cascadeFileName = env->GetStringUTFChars(cascadeFileName_, 0);

    // TODO

    env->ReleaseStringUTFChars(cascadeFileName_, cascadeFileName);
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_measuring_MainActivity_detect(JNIEnv *env, jobject instance,
                                               jlong casacadeClassifier_face,
                                               jlong cascadeClassifier_eye, jlong matAddrInput,
                                               jlong matAddrResult) {

    // TODO

}
JNIEXPORT jlong JNICALL Java_hoeun_opencv_1ndk_MainActivity_loadCascade
        (JNIEnv *env, jobject type, jstring cascadeFileName_){
    const char *nativeFileNameString = env->GetStringUTFChars(cascadeFileName_, 0);
    string baseDir("/storage/emulated/0/");
    baseDir.append(nativeFileNameString);
    const char *pathDir = baseDir.c_str();
    jlong ret = 0;
    ret = (jlong) new CascadeClassifier(pathDir);
    if (((CascadeClassifier *) ret)->empty()) {
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                            "CascadeClassifier로 로딩 실패 %s", nativeFileNameString);
    }
    else
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                            "CascadeClassifier로 로딩 성공 %s", nativeFileNameString);
    env->ReleaseStringUTFChars(cascadeFileName_, nativeFileNameString);
    return ret;
}
