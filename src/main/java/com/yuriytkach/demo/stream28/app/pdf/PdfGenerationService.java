package com.yuriytkach.demo.stream28.app.pdf;

import static net.sf.dynamicreports.report.builder.DynamicReports.cht;
import static net.sf.dynamicreports.report.builder.DynamicReports.cmp;
import static net.sf.dynamicreports.report.builder.DynamicReports.col;
import static net.sf.dynamicreports.report.builder.DynamicReports.grp;
import static net.sf.dynamicreports.report.builder.DynamicReports.hyperLink;
import static net.sf.dynamicreports.report.builder.DynamicReports.stl;
import static net.sf.dynamicreports.report.builder.DynamicReports.template;
import static net.sf.dynamicreports.report.builder.DynamicReports.type;

import java.awt.*;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.yuriytkach.demo.stream28.app.model.Funder;
import com.yuriytkach.demo.stream28.app.model.ReportType;

import lombok.extern.slf4j.Slf4j;
import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.jasper.constant.ImageType;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.ReportTemplateBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.ComponentBuilder;
import net.sf.dynamicreports.report.builder.datatype.DataTypes;
import net.sf.dynamicreports.report.builder.group.CustomGroupBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.VerticalTextAlignment;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.dynamicreports.report.exception.DRException;
import net.sf.jasperreports.engine.JREmptyDataSource;

@Slf4j
@Component
public class PdfGenerationService {

  public void createPdf(final List<Funder> funders, final OutputStream outputStream, final ReportType type) {
    try {
      // Create a fundersReport builder
      final JasperReportBuilder fundersReport = DynamicReports.report()
        .setTemplate(createReportingTemplate())
        .title(
          cmp.text("All Funders").setStyle(
            stl.style()
              .bold()
              .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
              .setTopPadding(20)
              .setBottomPadding(10)
          )
        );
      // Add columns to the fundersReport
      fundersReport.columns(
          col.reportRowNumberColumn("#").setWidth(20),
          col.column("Name", "name", DataTypes.stringType()),
          col.column("Funded At", "fundedDate", DataTypes.dateType())
            .setPattern("yyyy-MM-dd HH:mm"),
          col.column("Amount", "amount", DataTypes.integerType()),
          col.column("Currency", "currency", DataTypes.stringType())
        );
      // Add data to the fundersReport
      fundersReport.setDataSource(funders);

      // Optional grouping of rows
      CustomGroupBuilder yearGroup = grp.group(new AbstractSimpleExpression<String>() {
        @Override
        public String evaluate(final ReportParameters reportParameters) {
          final Date date = reportParameters.getValue("fundedDate");
          final var localDate = date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
          return String.format(
            "%s-%s",
            localDate.getMonth().getDisplayName(TextStyle.SHORT, Locale.US),
            localDate.getYear()
          );
        }
      });
      fundersReport.groupBy(yearGroup);

      // Create chart
      final JasperReportBuilder fundingChart = createChartReport(funders);

      // Create overall report with empty datasource
      final JasperReportBuilder totalReport = DynamicReports.report()
        .setTemplate(createReportingTemplate())
        .title(createTitleComponent())
        .pageFooter(createFooterComponent())
        .setDataSource(new JREmptyDataSource())
        .detail(
          cmp.subreport(fundingChart)
            .setStyle(stl.style().setBottomPadding(20))
        )
        .summary(
          cmp.subreport(fundersReport)
        );

      // Export the fundersReport
      switch (type) {
        case PDF -> totalReport.toPdf(outputStream);
        case DOCX -> totalReport.toDocx(outputStream);
        case JPG -> totalReport.toImage(outputStream, ImageType.JPG);
      }

      log.info("Done exporting file!");
    } catch (final DRException ex) {
      log.error("Failed: {}", ex.getMessage(), ex);
      throw new IllegalStateException("Failed to produce report: " + ex.getMessage(), ex);
    }
  }

  private JasperReportBuilder createChartReport(final List<Funder> funders) {
    final Map<LocalDate, Long> fundingByDate = funders.stream()
      .collect(Collectors.groupingBy(
        funder -> funder.getFundedAt().toLocalDate(),
        Collectors.counting()
      ));

    final var minDate = funders.stream()
      .map(Funder::getFundedAt)
      .map(LocalDateTime::toLocalDate)
      .min(Comparator.naturalOrder())
      .orElseThrow();

    final var maxDate = funders.stream()
      .map(Funder::getFundedAt)
      .map(LocalDateTime::toLocalDate)
      .max(Comparator.naturalOrder())
      .orElseThrow();

    final var entrySet = Stream.iterate(minDate, date -> date.plusDays(1))
      .limit(ChronoUnit.DAYS.between(minDate, maxDate) + 1)
      .map(date -> Map.entry(date, fundingByDate.getOrDefault(date, 0L)))
      .collect(Collectors.toSet());

    final DRDataSource dataSource = new DRDataSource("count", "date", "dayOfMonth");
    entrySet
      .stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(entry -> dataSource.add(
        entry.getValue(),
        Date.from(entry.getKey().atStartOfDay().toInstant(ZoneOffset.UTC)),
        entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.US) + "-" + entry.getKey().getDayOfMonth()
      ));

    final TextColumnBuilder<Long> columnCount = col.column("Count", "count", type.longType());
    final TextColumnBuilder<String> columnDayOfMonth = col.column("Date", "dayOfMonth", type.stringType());

    final var ichart = cht.barChart()
      .setTitle("Funding Count per Day")
      .setTitleFont(stl.fontArialBold())
      .setCategory(columnDayOfMonth)
      .series(cht.serie(columnCount))
      .seriesColors(Color.BLUE)
      .setValueAxisFormat(cht.axisFormat().setTickLabelMask("#")) // ticks are not decimal
      .setShowLegend(false)
      .setStyle(stl.style().setBorder(stl.pen1Point()));

    return DynamicReports.report()
      .summary(ichart)
      .setDataSource(dataSource);
  }

  private ReportTemplateBuilder createReportingTemplate() {
    final var rootStyle = stl.style().setPadding(2);
    final var columnStyle = stl.style(rootStyle).setVerticalTextAlignment(VerticalTextAlignment.MIDDLE);
    final var columnTitleStyle = stl.style(columnStyle)
      .setBorder(stl.pen1Point())
      .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
      .setBackgroundColor(Color.LIGHT_GRAY)
      .bold();

    return template().setLocale(Locale.US)
      .setColumnStyle(columnStyle)
      .setColumnTitleStyle(columnTitleStyle)
      .setGroupStyle(stl.style(rootStyle).bold().setHorizontalTextAlignment(HorizontalTextAlignment.LEFT))
      .setGroupTitleStyle(stl.style(rootStyle).bold().setHorizontalTextAlignment(HorizontalTextAlignment.LEFT))
      .highlightDetailEvenRows()
      .crosstabHighlightEvenRows();
  }

  private ComponentBuilder<?, ?> createTitleComponent() {
    return cmp.horizontalList(
      cmp.image(this.getClass().getClassLoader().getResource("images/fundraiser-repeater-round.png"))
        .setFixedDimension(75, 75)
        .setHyperLink(hyperLink("https://yuriytkach.com/volunteer")),
      cmp.text("Fundraiser Report")
        .setStyle(stl.style()
          .bold()
          .setTextAlignment(HorizontalTextAlignment.CENTER, VerticalTextAlignment.MIDDLE)
          .setFontSize(22)
        )
    )
      .newRow()
      .add(cmp.verticalGap(10));
  }

  private ComponentBuilder<?, ?> createFooterComponent() {
    return cmp.horizontalFlowList(
      cmp.text(new Date())
        .setStyle(stl.style().setFontSize(8).setHorizontalTextAlignment(HorizontalTextAlignment.LEFT)),
      cmp.pageXofY()
        .setStyle(stl.style().setFontSize(8).setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT))
    )
      .setStyle(stl.style().setTopPadding(10).setTopBorder(stl.pen1Point()));
  }

}
