//
// Created by Shiki on 2022/9/14.
//

#include "opencv_utils.h"

#include <cstdint>

#include <opencv2/core/utility.hpp>
#include <opencv2/stitching.hpp>
#include <opencv2/imgcodecs.hpp>

#include "android_log.h"

const float k_cut_black_threshold_ = 0.001;

OpencvUtils::OpencvUtils() {

}
OpencvUtils::~OpencvUtils() {

}
bool OpencvUtils::CheckRow(const cv::Mat &roi, int y) {
  int zero_count = 0;
  for (int x = 0; x < roi.cols; x++) {
	if (roi.at<uchar>(y, x)==0) {
	  zero_count++;
	}
  }
  if ((static_cast<float>(zero_count)/static_cast<float>(roi.cols)) > k_cut_black_threshold_) {
	return false;
  }
  return true;
}

bool OpencvUtils::CheckColumn(const cv::Mat &roi, int x) {
  int zero_count = 0;
  for (int y = 0; y < roi.rows; y++) {
	if (roi.at<uchar>(y, x)==0) {
	  zero_count++;
	}
  }
  if ((static_cast<float>(zero_count)/static_cast<float>(roi.rows)) > k_cut_black_threshold_) {
	return false;
  }
  return true;
}

bool OpencvUtils::CropLargestPossibleROI(const cv::Mat &gray, cv::Mat &pano, cv::Rect start_roi) {
  // evaluate start-ROI
  cv::Mat possible_roi = gray(start_roi);
  bool top_ok = CheckRow(possible_roi, 0);
  bool left_ok = CheckColumn(possible_roi, 0);
  bool bottom_ok = CheckRow(possible_roi, possible_roi.rows - 1);
  bool right_ok = CheckColumn(possible_roi, possible_roi.cols - 1);
  if (top_ok && left_ok && bottom_ok && right_ok) {
	// Found!!
	LOGD("cropLargestPossibleROI success");
	pano = pano(start_roi);
	return true;
  }
  // If not, scale ROI down
  cv::Rect new_roi(start_roi.x, start_roi.y, start_roi.width, start_roi.height);
  // if x is increased, width has to be decreased to compensate
  if (!left_ok) {
	new_roi.x++;
	new_roi.width--;
  }
  // same is valid for y
  if (!top_ok) {
	new_roi.y++;
	new_roi.height--;
  }
  if (!right_ok) {
	new_roi.width--;
  }
  if (!bottom_ok) {
	new_roi.height--;
  }
  if (new_roi.x + start_roi.width < 0 || new_roi.y + new_roi.height < 0) {
	LOGD("cropLargestPossibleROI failed")
	return false;
  }
  return CropLargestPossibleROI(gray, pano, new_roi);
}
bool OpencvUtils::Stitch(const std::string img_path_list[],
						 const std::string &out_file_path,
						 uint16_t img_width,
						 bool is_crop) {

  auto img_list = std::vector<cv::Mat>();
  cv::Mat img;
  cv::Mat img_scaled;
  uint16_t size = img_path_list->size();
  for (int i = 0; i < size; ++i) {
	auto path = img_path_list[i];
	LOGD("path:%s", path.c_str())
	img = cv::imread(img_path_list[i]);
	if (img.cols > img_width) {
	  float scale = static_cast<float >(img_width)/img.cols;
	  cv::Size dsize = cv::Size(static_cast<int>(img.cols*scale), static_cast<int>(img.rows*scale));
	  cv::resize(img, img_scaled, dsize);
	  img.release();
	  img_list.push_back(img_scaled);
	} else {
	  img_list.push_back(img);
	}
  }

  cv::Mat pano;
  auto stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);
  auto status = stitcher->stitch(img_list, pano);
  if (status!=cv::Stitcher::OK) {
	return static_cast<int >(status);
  }
  for (auto entry: img_list) {
	entry.release();
  }
  bool ret = false;
  if (is_crop) {
	cv::Mat pano_tocut;
	cv::Mat gray;
	pano_tocut = pano;
	cvtColor(pano_tocut, gray, cv::COLOR_BGR2GRAY);
	cv::Rect start_roi(0, 0, gray.cols, gray.rows);
	ret = CropLargestPossibleROI(gray, pano_tocut, start_roi);
	if (ret) {
	  cv::imwrite(out_file_path, pano_tocut);
	}
	pano_tocut.release();
	gray.release();
  }
  if (!ret) {
	cv::imwrite(out_file_path, pano);
  }
  pano.release();
  return true;
}
