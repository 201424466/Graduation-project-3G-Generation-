package com.example.measuring;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

class WatershedSegment {
    public Mat markers = new Mat();
    public Mat segmentation = new Mat();
    public Mat watershed = new Mat();

    public void setMarkers(Mat markerImage)
    {
        markerImage.convertTo(markers, CvType.CV_32S);
    }

    public void process(Mat image)
    {
        Imgproc.watershed(image, markers);
        markers.convertTo(markers, CvType.CV_8U);
    }

    public Mat getMarkers() {
        return this.markers;
    }


    public Mat getSegmentation() {
        markers.convertTo(segmentation, CvType.CV_8U);
        return this.segmentation;
    }

    public Mat getWatershed() {
        markers.convertTo(watershed, CvType.CV_8U, 150, 150);
        return this.watershed;
    }
}
