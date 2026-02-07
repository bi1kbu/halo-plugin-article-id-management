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
public class OperationLog {
    private String id;
    private String action;
    private String operator;
    private String targetId;
    private String targetCode;
    private String detail;
    private OffsetDateTime createdAt;
}
