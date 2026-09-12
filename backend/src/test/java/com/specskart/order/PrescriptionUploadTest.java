package com.specskart.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class PrescriptionUploadTest {

    @Autowired CartService carts;
    @Autowired PrescriptionFileRepository files;

    @Test
    void uploadingAPhotoMarksTheCartAndCanBeReadBack() {
        var cart = carts.getOrCreate(null);
        var file = new MockMultipartFile("file", "rx.jpg", "image/jpeg", "not a real photo".getBytes());

        var view = carts.uploadPrescription(cart.getToken(), file);

        assertThat(view.hasPrescription()).isTrue();
        assertThat(files.findAll()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectsAnUnsupportedFileType() {
        var cart = carts.getOrCreate(null);
        var file = new MockMultipartFile("file", "rx.exe", "application/x-msdownload", "nope".getBytes());
        assertThatThrownBy(() -> carts.uploadPrescription(cart.getToken(), file))
                .hasMessageContaining("photo");
    }
}
