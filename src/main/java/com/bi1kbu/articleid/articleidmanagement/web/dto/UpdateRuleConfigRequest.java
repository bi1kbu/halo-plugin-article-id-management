package com.bi1kbu.articleid.articleidmanagement.web.dto;

import com.bi1kbu.articleid.articleidmanagement.domain.RuleOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class UpdateRuleConfigRequest {
    @NotBlank
    private String prefix;
    @Min(2)
    @Max(8)
    private int serialWidth;
    @NotBlank
    private String codePattern;
    private boolean resetPerYear;
    @NotEmpty
    @Valid
    private List<RuleOption> departments;
    @NotEmpty
    @Valid
    private List<RuleOption> docTypes;
}
