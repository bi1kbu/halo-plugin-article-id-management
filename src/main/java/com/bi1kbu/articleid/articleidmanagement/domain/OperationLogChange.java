package com.bi1kbu.articleid.articleidmanagement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogChange {
    private String field;
    private String fromValue;
    private String toValue;
}

