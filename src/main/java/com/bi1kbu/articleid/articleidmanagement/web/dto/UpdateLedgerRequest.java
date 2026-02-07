package com.bi1kbu.articleid.articleidmanagement.web.dto;

import com.bi1kbu.articleid.articleidmanagement.domain.LedgerStatus;
import lombok.Data;

@Data
public class UpdateLedgerRequest {
    private LedgerStatus status;
    private String replacesCode;
    private String replacedByCode;
    private String dependencyCodes;
    private String remark;
}
