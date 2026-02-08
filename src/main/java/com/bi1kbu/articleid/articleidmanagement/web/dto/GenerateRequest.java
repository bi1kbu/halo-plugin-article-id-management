package com.bi1kbu.articleid.articleidmanagement.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateRequest {
    @NotBlank
    private String deptCode;
    @NotBlank
    private String docType;
    @Min(2000)
    @Max(2100)
    private int year;
    @Min(1)
    private Integer serial;
    @Min(1)
    private Integer subSerial;
    @Min(1)
    private Integer rev;
    private String replacesCode;
    private String replacedByCode;
    private String dependencyCodes;
    private String remark;
    private String articleName;
    private String articleTitle;
    private String articleLink;
    private String articlePublishedDate;
}
