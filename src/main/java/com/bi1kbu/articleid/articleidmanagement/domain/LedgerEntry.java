package com.bi1kbu.articleid.articleidmanagement.domain;

import java.time.OffsetDateTime;
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
}
