package org.niiish32x.sugarsms.app.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.print.attribute.standard.MediaSize;
import java.io.Serializable;
import java.util.List;

/**
 * AlertSpecDTO
 * 报警详细DTO
 * @author shenghao ni
 * @date 2024.12.25 10:06
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertSpecDTO implements Serializable {
    @JSONField(name = "displayName")
    private String displayName;
    private int priority;
    private boolean enable;
    private String operator;

    @JSONField(name = "limitValue")
    private String limitValue;
    private double deadBand;
    private String deadBandType;
    private String comment;
    private String alarmType;
    private int id;
    private String enName;
    private String templateEnName;
    private String templateDisplayName;
    private String instanceEnName;
    private String instanceDisplayName;
    private String attributeEnName;
    private String attributeDisplayName;
    private String attributeComment;

    @JSONField(name = "instanceLabels")
    private List<InstanceLabelDTO> instanceLabels;

    @JSONField(name = "attributeLabels")
    private List<AttributeLabelDTO>  attributeLabels;
}
