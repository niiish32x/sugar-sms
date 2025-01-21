package org.niiish32x.sugarsms.alert.app;

import org.niiish32x.sugarsms.alert.domain.entity.AlertRecordEO;
import org.niiish32x.sugarsms.api.alert.dto.AlertRecordDTO;
import org.niiish32x.sugarsms.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * AlertController
 *
 * @author shenghao ni
 * @date 2024.12.10 10:12
 */

@RestController
public class AlertController {

    @Resource
    AlertService alertService;

    @RequestMapping("/alerts/records")
    public Result<List<AlertRecordDTO>> getAlertRecords() {
        return alertService.queryAlertRecords();
    }

    @RequestMapping("/alerts")
    public Result getAlert() {
        return alertService.getAlertsFromSupos();
    }

    @PostMapping("/alerts/ack")
    public Result ackAlert() {
        return alertService.ackAlerts();
    }

}
