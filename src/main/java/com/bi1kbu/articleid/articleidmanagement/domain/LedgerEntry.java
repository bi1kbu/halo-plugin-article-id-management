package com.bi1kbu.articleid.articleidmanagement.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {
    private String id;
    private String fullCode;
    private String prefix;
    private String deptCode;
    private String docType;
    private int serial;
    private Integer subSerial;
    private int year;
    private Integer rev;
    private LedgerStatus status;
    private String articleName;
    private String articleTitleSnapshot;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private String replacesCode;
    private String replacedByCode;
    private String dependencyCodes;
    private String remark;
    private String articleTitle;
    private String articleLink;
    private String articlePublishedDate;
    private String effectiveDate;
    private String supersededDate;
    private String voidDate;

    public String getStatusDisplayKey() {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case REGISTERED -> "REGISTERED";
            case DELETED -> "DELETED";
            case VOID, SUPERSEDED -> "TERMINATED";
            case BOUND -> resolveBoundStatusDisplayKey();
        };
    }

    public String getStatusDisplay() {
        return switch (getStatusDisplayKey()) {
            case "REGISTERED" -> "已注册";
            case "DELETED" -> "已删除";
            case "TERMINATED" -> "废止";
            case "BOUND_PENDING" -> "即将生效";
            case "BOUND_EFFECTIVE" -> "现行有效";
            default -> "";
        };
    }

    private String resolveBoundStatusDisplayKey() {
        if (effectiveDate == null || effectiveDate.isBlank()) {
            return "BOUND_EFFECTIVE";
        }
        try {
            var today = LocalDate.now();
            var start = LocalDate.parse(effectiveDate);
            return start.isAfter(today) ? "BOUND_PENDING" : "BOUND_EFFECTIVE";
        } catch (DateTimeParseException ignored) {
            return "BOUND_EFFECTIVE";
        }
    }
}
