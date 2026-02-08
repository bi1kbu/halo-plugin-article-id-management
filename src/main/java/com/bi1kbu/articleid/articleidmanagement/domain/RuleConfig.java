package com.bi1kbu.articleid.articleidmanagement.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleConfig {
    private String prefix = "MS";
    private int serialWidth = 4;
    private String codePattern = "{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}";
    private boolean resetPerYear = true;
    private String bindingSourceType = "POST";
    private String bindingSourceUrl = "";
    private List<RuleOption> departments = new ArrayList<>();
    private List<RuleOption> docTypes = new ArrayList<>();
}
