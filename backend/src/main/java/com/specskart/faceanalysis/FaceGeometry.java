package com.specskart.faceanalysis;

/**
 * Normalized facial ratios supplied by the browser (MediaPipe Face Landmarker).
 * All widths/heights are already divided by face height so they are scale-invariant.
 * No raw landmarks or image data are required server-side.
 */
public record FaceGeometry(
        double faceWidthRatio,      // cheekbone width / face height
        double foreheadWidthRatio,  // forehead width / face height
        double cheekboneWidthRatio, // cheekbone width / face height
        double jawWidthRatio,       // jaw width / face height
        double jawAngleDeg,         // jaw contour angle in degrees
        double chinRatio            // chin height / face height
) {}
