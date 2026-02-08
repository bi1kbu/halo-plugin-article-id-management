package com.bi1kbu.articleid.articleidmanagement.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicLookupResponse {
    private String ledgerId;
    private String fullCode;
    private String status;
    private String statusDisplayKey;
    private String statusDisplay;
    private String effectiveDate;
    private String supersededDate;
    private String voidDate;
    private String issuingAuthority;
    private String deptCode;
    private String docType;
    private String articleTitle;
    private String articleLink;
    private String articlePublishedDate;
}
