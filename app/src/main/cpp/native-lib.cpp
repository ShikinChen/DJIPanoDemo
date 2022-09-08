#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>

extern "C" JNIEXPORT jstring JNICALL
Java_me_shiki_djipanodemo_MainActivity_opencvVersion(
	JNIEnv *env,
	jobject /* this */) {
  std::string info = "opencv:";
  info += CV_VERSION;
  return env->NewStringUTF(info.c_str());
}