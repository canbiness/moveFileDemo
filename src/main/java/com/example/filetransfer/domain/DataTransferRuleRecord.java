package com.example.filetransfer.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("d_data_transfer_rule_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTransferRuleRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ruleName;

    private String path;

    private String targetPath;

    private String location;

    private Integer fromDate;

    private Integer endDate;

    private String fileName;

    private String folderName;

    private String regex;

    private String suffixTemp;

    private Long ruleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
