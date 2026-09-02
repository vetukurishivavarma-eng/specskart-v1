package com.specskart.faceanalysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaceShapeClassifierTest {

    private final FaceShapeClassifier classifier = new FaceShapeClassifier();

    @Test
    void classifiesALongAngularFaceAsRectangle() {
        var r = classifier.classify(new FaceGeometry(0.60, 0.58, 0.60, 0.58, 118, 0.20));
        assertThat(r.faceShape()).isIn("RECTANGLE", "SQUARE");
        assertThat(r.confidence()).isBetween(0.45, 0.98);
    }

    @Test
    void classifiesAWideForeheadNarrowJawAsHeart() {
        var r = classifier.classify(new FaceGeometry(0.80, 0.82, 0.80, 0.66, 140, 0.18));
        assertThat(r.faceShape()).isEqualTo("HEART");
    }

    @Test
    void classifiesWidestCheeksAsDiamond() {
        var r = classifier.classify(new FaceGeometry(0.86, 0.70, 0.86, 0.70, 130, 0.20));
        assertThat(r.faceShape()).isIn("DIAMOND", "OVAL");
    }

    @Test
    void alwaysReturnsAShapeAndBoundedConfidence() {
        var r = classifier.classify(new FaceGeometry(0.75, 0.74, 0.75, 0.74, 135, 0.19));
        assertThat(r.faceShape()).isNotBlank();
        assertThat(r.confidence()).isLessThanOrEqualTo(0.98);
        assertThat(r.scores()).isNotEmpty();
    }
}
