#include <jni.h>
#include <string>
#include <stdint.h>
#include <opencv2/opencv.hpp>
#include "android_log.h"
#include "opencv_utils.h"

#define NATIVE_UTILS_CLASS_NAME "me/shiki/djipanodemo/OpenCVUtils"

#ifdef __cplusplus
extern "C" {
#endif


JNIEXPORT jstring JNICALL native_OpenCVVersion(
	JNIEnv *env,
	jobject instance) {
  std::string info = "opencv:";
  info += CV_VERSION;
  return env->NewStringUTF(info.c_str());
}

JNIEXPORT jboolean JNICALL
native_Stitch(JNIEnv *env,
			  jclass clazz,
			  jobjectArray j_img_path_list,
			  jstring j_out_file_path,
			  jint j_img_width,
			  jboolean j_is_crop) {
  uint16_t img_size = env->GetArrayLength(j_img_path_list);
  std::string img_path_list[img_size];
  jboolean j_is_copy = JNI_FALSE;
  for (int i = 0; i < img_size; ++i) {
	jstring j_img_path = (jstring)(env->GetObjectArrayElement(j_img_path_list, i));
	const char *img_path = env->GetStringUTFChars(j_img_path, &j_is_copy);
	img_path_list[i] = img_path;
	env->ReleaseStringUTFChars(j_img_path, img_path);
  }

  const char *out_file_path = env->GetStringUTFChars(j_out_file_path, &j_is_copy);
  bool ret = OpencvUtils::Stitch(img_path_list, out_file_path, j_img_width, j_is_crop);
  env->ReleaseStringUTFChars(j_out_file_path, out_file_path);
  return ret;
}

static JNINativeMethod g_NativeUtilsMethods[] = {
	{"native_OpenCVVersion", "()Ljava/lang/String;", (void *)(native_OpenCVVersion)},
	{"native_Stitch", "([Ljava/lang/String;Ljava/lang/String;IZ)Z", (void *)(native_Stitch)},
};

static int RegisterNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods, int methodNum) {
  LOGD("RegisterNativeMethods");
  jclass clazz = env->FindClass(className);
  if (!clazz) {
	LOGE("RegisterNativeMethods fail. clazz == NULL");
	return JNI_FALSE;
  }
  if (env->RegisterNatives(clazz, methods, methodNum) < 0) {
	LOGE("RegisterNativeMethods fail");
	return JNI_FALSE;
  }
  return JNI_TRUE;
}

static void UnregisterNativeMethods(JNIEnv *env, const char *className) {
  LOGD("UnregisterNativeMethods");
  jclass clazz = env->FindClass(className);
  if (!clazz) {
	LOGE("UnregisterNativeMethods fail. clazz == NULL");
	return;
  }
  if (env) {
	env->UnregisterNatives(clazz);
  }
}

JNIEXPORT JNICALL jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
  jint jniRet = JNI_ERR;
  JNIEnv *env = nullptr;
  if (jvm->GetEnv((void **)(&env), JNI_VERSION_1_6)!=JNI_OK) {
	return jniRet;
  }
  jint regRet = RegisterNativeMethods(env, NATIVE_UTILS_CLASS_NAME, g_NativeUtilsMethods,
									  sizeof(g_NativeUtilsMethods)/
										  sizeof(g_NativeUtilsMethods[0]));

  if (regRet!=JNI_TRUE) {
	return JNI_ERR;
  }
  return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *jvm, void *p) {
  JNIEnv *env = nullptr;
  if (jvm->GetEnv((void **)(&env), JNI_VERSION_1_6)!=JNI_OK) {
	return;
  }

  UnregisterNativeMethods(env, NATIVE_UTILS_CLASS_NAME);
}

#ifdef __cplusplus
}
#endif