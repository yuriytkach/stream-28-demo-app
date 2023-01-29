package com.yuriytkach.demo.stream28.app.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.yuriytkach.demo.stream28.app.config.ApiProperties;
import com.yuriytkach.demo.stream28.app.model.Funder;
import com.yuriytkach.demo.stream28.app.model.ReportType;
import com.yuriytkach.demo.stream28.app.pdf.PdfGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PdfCreateController {

  private static final String APPLICATION_PDF = "application/pdf";
  private static final String IMAGE_JPG = "image/jpeg";
  private static final String APPLICATION_DOCX =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private final RestTemplate restTemplate;
  private final PdfGenerationService pdfGenerationService;
  private final ApiProperties apiProperties;

  /**
   * Request report with different types:
   * - url/report?type=PDF
   * - url/report?type=DOCX
   * - url/report?type=JPG
   */
  @GetMapping(value = "/report", produces = { APPLICATION_PDF, IMAGE_JPG, APPLICATION_DOCX })
  public ResponseEntity<byte[]> getReportFile(
    @RequestParam(required = false, defaultValue = "PDF") final ReportType type
  ) throws IOException {
    log.info("Loading funders from external api...");
    final Funder[] funders = restTemplate.getForObject(apiProperties.getUrl(), Funder[].class);

    log.info("Loaded funders: {}", funders.length);

    log.info("Producing pdf...");
    try(ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
      pdfGenerationService.createPdf(List.of(funders), outStream, type);

      final byte[] bytes = outStream.toByteArray();
      log.info("Produced file size: {} KiB", bytes.length / 1024);

      final MediaType contentType = switch (type) {
        case PDF -> MediaType.APPLICATION_PDF;
        case DOCX -> MediaType.parseMediaType(APPLICATION_DOCX);
        case JPG -> MediaType.IMAGE_JPEG;
      };
      return ResponseEntity.ok()
        .contentType(contentType)
        .body(bytes);
    }
  }

}
