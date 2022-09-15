//
// Created by Shiki on 2022/9/14.
//

#ifndef ANDROID_OPENCV_UTILS_H_
#define ANDROID_OPENCV_UTILS_H_

#include <opencv2/core.hpp>
#include <stdint.h>

class OpencvUtils {
 private:
  explicit OpencvUtils();
  ~OpencvUtils();
 public:
  static bool Stitch(const std::string img_path_list[],
					 const std::string &out_file_path,
					 uint16_t img_width,
					 bool is_crop);
 private:
  static bool CheckRow(const cv::Mat &roi, int y);
  static bool CheckColumn(const cv::Mat &roi, int x);
  static bool CropLargestPossibleROI(const cv::Mat &gray, cv::Mat &pano, cv::Rect start_roi);
};

#endif //ANDROID_OPENCV_UTILS_H_
